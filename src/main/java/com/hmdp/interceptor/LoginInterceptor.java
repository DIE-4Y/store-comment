package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session中的数据
//        HttpSession session = request.getSession();
//        UserDTO user =(UserDTO) session.getAttribute("user");

        //判断token是否为空
        String token = request.getHeader("authorization");
//        log.info("拦截器获取到token：{}", token);
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }

        //获取redis中缓存的用户信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if(MapUtil.isEmpty(userMap)){
            response.setStatus(401);
            return false;
        }

        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        log.info("拦截器获取到用户:{}",user);
        //判断user是否存在
//        if(user == null){
//            response.setStatus(401);
//            return false;
//        }

        //存入ThreadLocal
        UserHolder.saveUser(user);

        //设置过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
