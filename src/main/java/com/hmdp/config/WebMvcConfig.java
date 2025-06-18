package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    //登录拦截器
    private final LoginInterceptor loginInterceptor;

    //token刷新拦截器
    private final RefreshTokenInterceptor refreshTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(refreshTokenInterceptor)
            .addPathPatterns("/**")
            .order(0);//设置优先级 越低越早执行

    registry.addInterceptor(loginInterceptor)
            .excludePathPatterns(
                    "/user/code",
                    "/user/login",
                    "/blog/hot",
                    "/upload/**",
                    "shop-type/**",
                    "voucher/**",
                    "shop/**"
                    )
            .order(1);
    }



}
