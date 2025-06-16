package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
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

        //发送验证码
        log.debug("手机验证码是："+code);

        return Result.ok();
    }
}
