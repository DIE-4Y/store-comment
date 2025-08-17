package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: chenshanquan
 * @CreateTime: 2025-08-17
 * @Description: Redisson配置类
 */

@Configuration
public class RedissonConfig {

	@Bean
	public RedissonClient redissonClient() {
		Config config = new Config();
		// 采用单个服务器模式
		config.useSingleServer().setAddress("redis://192.168.59.129:6379").setPassword("root");
		return Redisson.create(config);
	}
}
