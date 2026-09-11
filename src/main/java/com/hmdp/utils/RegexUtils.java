package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 格式校验工具：用正则判断手机号/邮箱/验证码是否“无效”。
 *
 * 实现套路：所有 isXxxInvalid 都调用私有的 mismatch(str, regex)，
 * 先判空（空也视为无效），再用 String.matches 比对 RegexPatterns 里的正则。
 * 注意方法名叫“isInvalid”（是否无效），返回 true 表示“不合规范”，别看反了。
 *
 * @author 虎哥
 */
public class RegexUtils {
    // 注意：方法名是 isInvalid，返回 true 代表"不合规范"，别看反了
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // 空也视为无效；否则用正则比对（取反后返回是否"不符合"）
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
