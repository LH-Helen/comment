package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
public class AddUserTest {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void addUserRedisToken() throws IOException {

        File f = new File("token.txt");
        FileOutputStream fileOutputStream = new FileOutputStream(f);

        OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, "UTF-8");


        List<User> users = userService.list();
        for(User user: users){
            String token = UUID.randomUUID().toString(true);
            // 将User对象转为HashMap存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            // 存储
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token, userMap);
            stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
            writer.append(token).append("\n");
        }

        writer.close();
        fileOutputStream.close();
    }
}
