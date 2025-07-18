package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisIdWorker {
	private static final long BEGIN_TIMESTAMP = 1735689600L;
	private final StringRedisTemplate stringRedisTemplate;

	public long nextId(String keyPrefix) {
		LocalDateTime now = LocalDateTime.now();
		long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
		long timeStamp = nowSeconds - BEGIN_TIMESTAMP;
		// 将当前时间转为yyyy:MM:dd格式
		String timePrefix = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

		// Redis生成自增id
		long incrementId = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + timePrefix);
		return timeStamp << 32 | incrementId; // 左移32位或上id
	}

}
