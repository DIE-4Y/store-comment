package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
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

    @Override
        public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/user/**",
                        "/blog/**",
                        "/user/**",
                        "/follow/**",
                        "/blog-comments",
                        "/voucher-order")
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot"
                        );
    }

}
