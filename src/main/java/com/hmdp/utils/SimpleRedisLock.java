package com.hmdp.utils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import cn.hutool.core.lang.UUID;

public class SimpleRedisLock implements ILock {
	private String name;
	private final StringRedisTemplate stringRedisTemplate;

	private static final String REDIS_PREFIX = "lock:";
	private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
	// 释放锁的脚本
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

	public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
		this.name = name;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	static {
		// 给UNLOCK_SCRIPT脚本赋值
		UNLOCK_SCRIPT = new DefaultRedisScript<>();
		UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
		UNLOCK_SCRIPT.setResultType(Long.class);
	}

	@Override
	public boolean tryLock(long timeOutSec) {
		long threadId = Thread.currentThread().getId();
		Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(REDIS_PREFIX + name, ID_PREFIX + threadId,
				timeOutSec, TimeUnit.SECONDS);
		return Boolean.TRUE.equals(success);
	}

	// @Override
	// public void unlock() {
	// String threadId = stringRedisTemplate.opsForValue().get(REDIS_PREFIX + name);
	// // 如果获取线程id后从redis中被删除但是还未开始时比较，其他线程又写入，则会误删，不能实现原子性
	// if (threadId.equals(ID_PREFIX + Thread.currentThread().getId())) {
	// stringRedisTemplate.delete(REDIS_PREFIX + name);
	// }
	// }

	/**
	 * 采用lua脚本实现删除锁的逻辑，保证原子性
	 * 
	 * @author chenshanquan
	 * @date 2025/8/17 21:28
	 * @return void
	 **/
	@Override
	public void unlock() {
		// 采用lua脚本实现删除锁的逻辑，保证原子性
		stringRedisTemplate.execute(UNLOCK_SCRIPT, // lua脚本
				Collections.singletonList(REDIS_PREFIX + name), // KEY参数
				ID_PREFIX + Thread.currentThread().getId()// VALUE参数
		);
	}
}
