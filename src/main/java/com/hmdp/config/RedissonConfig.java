package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
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

	// @Bean
	// public RBloomFilter<Object> bloomFilter() {
	// Config config = new Config();
	// config.useSingleServer().setAddress("redis://192.168.59.129:6379").setPassword("root");
	// RedissonClient redissonClient = Redisson.create(config);
	// // 默认布隆过滤器
	// RBloomFilter<Object> bloomFilter =
	// redissonClient.getBloomFilter("bloomFilter");
	// // 初始化布隆过滤器（预计插入数量，错误率fpp）
	// bloomFilter.tryInit(10 * 10000, 0.01);
	// return bloomFilter;
	// }

	@Bean
	public RBloomFilter<String> bloomFilter(RedissonClient redissonClient) {
		RBloomFilter<String> filter = redissonClient.getBloomFilter("bloomFilter");
		filter.tryInit(100000, 0.01); // 预计 10 w 元素，误判率 1 %
		return filter;
	}
}
