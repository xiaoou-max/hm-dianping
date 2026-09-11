package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

// 用户业务：登录用"随机Token + Redis"方案（不是Session也不是JWT）
// 流程：UUID当token -> 脱敏UserDTO存Redis Hash -> token返回前端；每次请求带token刷新有效期
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    // Redis操作模板：验证码、登录态、签到数据都存Redis
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 发送验证码：校验手机号 -> 生成6位随机码 -> 存Redis（2分钟有效）
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号格式，无效直接返回错误
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 生成6位随机数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 验证码存Redis：key=login:code:手机号，有效期2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送短信（教学项目只打日志）
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    // 登录/注册：校验验证码 -> 查/建用户 -> 生成token -> 用户信息存Redis -> 返回token
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 取出前端传的手机号
        String phone = loginForm.getPhone();
        // 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 从Redis取出该手机号对应的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 取出前端传的验证码
        String code = loginForm.getCode();
        // 比对验证码：Redis里没有 或 与输入不一致，都算错误
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 按手机号查用户
        User user = query().eq("phone", phone).one();
        // 用户不存在：首次登录，自动注册（用手机号创建新用户）
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 生成token：UUID去横线，得到32位随机字符串
        String token = UUID.randomUUID().toString(true);
        // 脱敏拷贝：User -> UserDTO（只带id/nickName/icon，密码永远不离开服务端）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 把DTO转成Map才能写进Redis Hash；CopyOptions忽略null字段，并把所有值转成String（Redis Hash的value只能是字符串）
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 组装登录态的redis key：login:token:xxx
        String tokenKey = LOGIN_USER_KEY + token;
        // 把用户Map写入Redis Hash（HMSET）
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置登录态有效期（30分钟，每次访问会滑动续期）
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 返回token给前端，前端自己保存，后续请求放在header里
        return Result.ok(token);
    }

    // 当日签到：用Redis Bitmap的SETBIT把"今天是第几天"对应的位设为1；每月一个key，省空间
    @Override
    public Result sign() {
        // 取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // key后缀 = :yyyyMM（按月份区分），最终key=sign:用户id:yyyyMM
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 今天是本月的第几天（1~31）
        int dayOfMonth = now.getDayOfMonth();
        // SETBIT：把第(dayOfMonth-1)位设为1，表示今天签到了（bit从0开始数）
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    // 统计连续签到天数：用BITFIELD把本月签到位读成整数，再从最低位（今天）往高位数连续的1
    @Override
    public Result signCount() {
        // 取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // key后缀 = :yyyyMM，与签到用的key保持一致
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // BITFIELD：把本月签到的第0~dayOfMonth-1位读成一个无符号整数，二进制位上1=当天签过到
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        // 没读到签到记录，返回0
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        // 读出的整数是0，说明本月一天都没签，返回0
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 从最低位（代表今天）开始数连续的1：数到一个就+1，然后右移看下一位
        int count = 0;
        while (true) {
            // 当前位是0，说明连续签到断了，停止
            if ((num & 1) == 0) {
                break;
            }
            // 当前位是1，连续天数+1
            count++;
            // 无符号右移一位，把下一位挪到最低位继续判断
            num >>>= 1;
        }
        return Result.ok(count);
    }

    // 用手机号创建新用户（首次登录自动注册）
    private User createUserWithPhone(String phone) {
        User user = new User();
        // 设置手机号
        user.setPhone(phone);
        // 生成默认昵称：user_ + 10位随机字符串
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存到数据库
        save(user);
        return user;
    }
}
