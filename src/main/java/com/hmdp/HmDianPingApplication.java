package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true) // 暴露代理对象才能获取
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianPingApplication {

	public static void main(String[] args) {
		SpringApplication.run(HmDianPingApplication.class, args);
	}

}
