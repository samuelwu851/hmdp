package com.hmdp.utils;


import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class PasswordEncoder {

    public static String encode(String password) {
        // generate salt
        String salt = RandomUtil.randomString(20);
        // encode password
        return encode(password,salt);
    }
    private static String encode(String password, String salt) {
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("pattern of password is wrong！");
        }
        String[] arr = encodedPassword.split("@");

        String salt = arr[0];

        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
