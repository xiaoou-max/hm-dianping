# hm-dianping

本地生活点评类应用的后端服务，覆盖商铺查询、优惠券秒杀、探店笔记与关注等核心业务。

## 技术栈

| 组件 | 用途 |
|---|---|
| Spring Boot | 应用框架 |
| MyBatis-Plus | 数据访问层 |
| MySQL | 业务数据持久化 |
| Redis (Lettuce) | 缓存、登录态、秒杀库存 |
| Redisson | 分布式锁 |
| Hutool | 通用工具库 |
| Lombok | 简化样板代码 |

## 功能模块

| 模块 | 说明 |
|---|---|
| 商铺 `Shop` | 商铺列表、分类查询、按距离与评分排序 |
| 优惠券 `Voucher` | 普通券与秒杀券的发放与查询 |
| 秒杀下单 `VoucherOrder` | Redis + Lua 原子扣减库存，异步落库 |
| 探店笔记 `Blog` | 发布、浏览、点赞、评论 |
| 关注 `Follow` | 关注/取关、共同关注 |
| 用户 `User` | 登录、验证码、用户信息 |

## 技术要点

**秒杀下单**

`resources/seckill.lua` 在 Redis 中一次性完成「库存校验 + 一人一单校验 + 库存扣减」，避免高并发下超卖。下单请求写入阻塞队列，由独立线程异步落库，把数据库压力从请求链路中剥离。

**分布式锁**

`resources/unlock.lua` 保证解锁的原子性——先比对锁的持有者标识，确认是自己持有的锁再删除，避免误删他人锁。可重入场景使用 Redisson。

**缓存**

商铺详情走 Redis 缓存，配合逻辑过期与空值缓存处理缓存击穿和穿透。

**登录**

登录成功后签发 token 存入 Redis，由拦截器统一校验登录态。

## 目录结构

```
src/main/java/com/hmdp/
├── config/       配置类（MyBatis-Plus、Redis、Redisson、MVC、全局异常）
├── controller/   REST 接口
├── dto/          请求与响应对象
├── entity/       数据库实体
├── mapper/       MyBatis-Plus Mapper
├── service/      业务逻辑及其实现
└── utils/        工具类（Redis ID 生成器、登录拦截器等）

src/main/resources/
├── db/hmdp.sql   建表语句与初始数据
├── mapper/       MyBatis XML 映射
├── seckill.lua   秒杀库存扣减脚本
└── unlock.lua    分布式锁释放脚本
```

## 数据准备

```bash
mysql -uroot -p < src/main/resources/db/hmdp.sql
```

## 运行

修改 `src/main/resources/application.yaml` 中的 MySQL 与 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root
    password: <你的密码>
  redis:
    host: 127.0.0.1
    port: 6379
    password: <你的密码>
```

启动：

```bash
mvn spring-boot:run
```

默认端口 `8081`。
