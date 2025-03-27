package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.controller.UserController;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SyntacticSugar;
import lombok.extern.slf4j.Slf4j;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. check phone number
        if (RegexUtils.isPhoneInvalid(phone)) {
            //1.1 if not fit -> return
            return Result.fail("pattern of phone is not right");
        }
        //2.if correspond -> generate verification code and save to redis
        String verifyCode = SyntacticSugar.generateVerifyCode(4);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, verifyCode, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //3.send code
        log.debug("verifyCode : {}",verifyCode);
        //4.return
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. check phone number and verification code
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if((!cacheCode.equals(code)) || cacheCode == null){
            //1.1 if not match -> return false
            return Result.fail("phone number or verification code is wrong");
        }
        //2. if match -> query user by phone number
        User user = query().eq("phone", phone).one();
        //2.1 if exist -> login
        if(user == null){
            //2.2 if not exist -> create new user
            user = createUserWithPhone(phone);

        }
        //3.save user to redis
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        /**
         *
         */
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL ,TimeUnit.MINUTES);


        //4. return
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


}

