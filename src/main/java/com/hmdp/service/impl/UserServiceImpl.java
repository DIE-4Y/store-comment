package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机格式
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误！");
        }

        //生成手机验证码
        String code = RandomUtil.randomNumbers(6);

        //存入session
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);

        //发送验证码
        log.debug("手机验证码是："+code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误！");
        }

        //校验验证码是否和手机匹配
        Object cachePhone = session.getAttribute("phone");
        Object cacheCode = session.getAttribute("code");

        if(!phone.equals(cachePhone) || !loginForm.getCode().equals(cacheCode)){
            return Result.fail("手机或验证码错误！");
        }

        //判断用户是否存在
        User user = query().eq("phone", phone).one();

        //用户不存在--创建
        if(user == null){
            user = generateUserWithPhone(phone);
        }

        //保存用户信息到session
        session.setAttribute("user", user);

        return Result.ok();
    }

    //根据phone生成用户
    private User generateUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

}
