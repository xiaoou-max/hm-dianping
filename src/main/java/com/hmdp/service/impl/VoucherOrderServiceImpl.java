package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 秒杀下单业务，也是全项目设计最复杂、最能体现分布式锁演进的地方
// 整体思路（异步削峰）：
//   1) seckillVoucher：Lua脚本在Redis端原子完成"校验资格+扣库存+发消息到Stream"，瞬间返回订单id
//   2) 后台线程VoucherOrderHandler从Stream读消息，调createVoucherOrder真正建单
//   3) createVoucherOrder：Redisson锁（按userId）保证一人一单，锁内查重+扣库存+建单
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    // 秒杀券服务：用来查/扣优惠券库存
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    // 全局唯一ID生成器：生成订单id
    @Resource
    private RedisIdWorker redisIdWorker;

    // Redisson客户端：拿分布式锁用（比手写锁强在可重入+自动续期）
    @Resource
    private RedissonClient redissonClient;

    // Redis操作模板：执行Lua脚本、读写Stream消息
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 秒杀Lua脚本对象（类加载时解析一次，秒杀时直接执行，不用每次读文件）
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 静态代码块：类第一次被使用时执行，用来初始化Lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 指定lua脚本位置（classpath下的seckill.lua）
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 指定脚本返回类型（Redis执行Lua返回Long）
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 单线程线程池：只启动一个后台消费者，保证订单按顺序创建
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 应用启动后：立即启动后台消费者线程，开始监听Stream里的下单消息
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 后台订单消费者：阻塞监听Stream新消息，拿到消息就建单；遇到异常先把pending里的积压消息处理掉
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            // 无限循环，持续消费消息
            while (true) {
                try {
                    // XREADGROUP：以消费者组g1的成员c1身份，阻塞读取新消息（最多等2秒）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 没读到消息（2秒超时），继续循环等下一轮
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 取第一条消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 把消息里的Map反序列化成订单对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 真正建单（加锁、查重、扣库存、保存）
                    createVoucherOrder(voucherOrder);
                    // XACK确认：消息已处理完，从pending-list移除；不确认的消息会留在pending-list等补偿
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    // 处理出错：先把pending-list里积压的未确认消息补偿掉，再继续监听新消息
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        // 处理pending-list：把异常期间积压、未确认的消息补偿处理掉
        private void handlePendingList() {
            // 无限循环，直到pending里的消息处理完
            while (true) {
                try {
                    // 从pending-list读取未确认消息（ReadOffset.from("0")表示从pending开始读）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // pending里没消息了，退出循环
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 反序列化成订单对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 建单
                    createVoucherOrder(voucherOrder);
                    // XACK确认，把这条消息从pending移除
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    // 补偿也失败就记日志继续下一轮（生产要加重试上限，避免死循环）
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /* 演进 v1：内存阻塞队列版（单 JVM 有效、重启丢消息，已废弃，教学对比用）
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // take()：从队列取订单；队列为空时阻塞等待，线程不会空转
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 在后台线程里走"锁 + 查重 + 扣库存 + 建单"完整流程
                    createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    // 真正下单：加Redisson锁保证"一人一单"，锁内查重、扣库存、保存订单
    // 用Redisson而非手写锁：看门狗自动续期，业务跑超时锁也不会提前过期
    private void createVoucherOrder(VoucherOrder voucherOrder) {
        // 取出订单里的用户id和券id
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 拿一把按userId命名的锁：同一用户只能有一个线程进临界区（不同用户互不阻塞）
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试加锁：无参tryLock表示不等待、不指定过期时间（启用看门狗自动续期）
        boolean isLock = redisLock.tryLock();
        // 没抢到锁：说明这个用户正在并发下单，直接拒绝
        if (!isLock) {
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 锁内第一步：查这个用户是否已买过这张券（一人一单的查重）
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 已经买过，拒绝重复下单
            if (count > 0) {
                log.error("不允许重复下单！");
                return;
            }

            // 锁内第二步：扣减库存；where带 stock > 0 做数据库层兜底（CAS思想，防超卖）
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // 库存减1
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0) // 条件：库存必须大于0
                    .update();
            // 扣减失败：库存已经为0
            if (!success) {
                log.error("库存不足！");
                return;
            }

            // 锁内第三步：保存订单
            save(voucherOrder);
        } finally {
            // 释放锁：放finally里，就算业务抛异常也能解锁，避免死锁
            redisLock.unlock();
        }
    }

    // 秒杀入口：执行Lua脚本做"校验+扣库存+发消息"，全部原子完成，秒回订单id
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 先生成全局唯一订单id（RedisIdWorker）
        long orderId = redisIdWorker.nextId("order");

        // 执行seckill.lua：Redis端原子完成"校验库存/重复下单 + 扣库存 + 发消息到Stream"
        // 返回：0=成功抢到，1=库存不足，2=重复下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),   // KEYS数组（脚本里没用到KEYS，传空）
                voucherId.toString(), userId.toString(), String.valueOf(orderId)   // 对应ARGV[1]~[3]
        );
        int r = result.intValue();
        // 结果不是0：说明没抢到，1是库存不足、2是重复下单
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 抢到了：返回订单id，真正建单由后台线程消费Stream消息执行（异步削峰）
        return Result.ok(orderId);
    }

    /* 演进 v2：Lua 校验 + 内存阻塞队列（建单仍走 v1 队列，集群丢单，已废弃）
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        // 2.5.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入阻塞队列
        orderTasks.add(voucherOrder);

        // 3.返回订单id
        return Result.ok(orderId);
    }*/
    /* 演进 v3：Lua 校验 + 同步建单（建单在请求线程内做，高并发响应慢，已废弃）
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return createVoucherOrder(voucherId);
    }



    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/
    /* 演进 v4：SimpleRedisLock 手写锁版（不续期、不可重入，已废弃，教学对比用）
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 手写锁按 userId 隔离；SimpleRedisLock 不是 Spring Bean，只能手动 new 并传入 Redis 模板
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // tryLock(1200)：手写锁不自动续期，业务跑超时会被抢锁（Redisson 看门狗解决的缺陷）
        boolean isLock = redisLock.tryLock(1200);
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/

    /* 演进 v0：synchronized 单机锁版（集群下完全失效的反面教材，已废弃）
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // intern()：让相同 userId 的字符串是同一对象，从而共用一把内置锁；缺陷：只锁单 JVM，集群失效
        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }*/
}
