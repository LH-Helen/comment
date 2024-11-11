package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public void sendCode(String phone, HttpSession session) {
        // 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            throw new RuntimeException("手机号格式错误");
        }
        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到session
        session.setAttribute(phone, code);
        // 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
    }

    @Override
    public void login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            throw new RuntimeException("手机号格式错误");
        }
        // 校验验证码
        Object cachecode = session.getAttribute(phone);
        String code = loginForm.getCode();
        if(cachecode == null ||!cachecode.toString().equals(code)){
            // 不一致，报错
            throw new RuntimeException("验证码错误");
        }

        // 判断用户是否存在
        User user = query().eq("phone", phone).one();

        // 不存在，创建新用户并保存
        if(user == null){
            user = createUserWithPhone(phone);
        }

        // 保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

    }

    private User createUserWithPhone(String phone) {
        // 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        // 保存用户
        save(user);
        return user;
    }
}
