package com.hmdp.utils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 封装Redis缓存方法
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheUtil {

	private final StringRedisTemplate stringRedisTemplate;
	// 线程池
	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(1);

	public void set(String key, Object value, Long time, TimeUnit timeUnit) {
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
	}

	/**
	 * 逻辑过期缓存
	 */
	public <T> void setWithLogicalExpire(String key, T value, Long time, TimeUnit timeUnit) {

		RedisData<T> redisData = new RedisData<>();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
	}

	public <T> T get(String key, Class<T> type) {
		String value = stringRedisTemplate.opsForValue().get(key);
		return value == null ? null : JSONUtil.toBean(value, type);
	}

	/**
	 * 存储空值解决缓存穿透
	 */
	public <T, ID> T getWithPassThrough(String prefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time,
			TimeUnit timeUnit) {
		String key = prefix + id;
		String json = stringRedisTemplate.opsForValue().get(key);
		if (StrUtil.isNotBlank(json)) {
			return JSONUtil.toBean(json, type);
		}

		if (json != null) {
			return null;
		}

		// 查询数据库
		T result = dbFallback.apply(id);

		// 缓存重建
		if (result == null) {
			stringRedisTemplate.opsForValue().set(key, "", time, timeUnit);
			return null;
		}

		set(key, result, time, timeUnit);
		return result;
	}

	/**
	 * 互斥解决缓存击穿
	 */
	public <T, ID> T getWithMutex(String prefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time,
			TimeUnit timeUnit) {

		String key = prefix + id;
		String json = stringRedisTemplate.opsForValue().get(key);
		if (StrUtil.isNotBlank(json)) {
			return JSONUtil.toBean(json, type);
		}

		if (json != null) {
			return null;
		}

		// 缓存重建
		if (tryLock(id)) {
			T apply = dbFallback.apply(id);

			if (apply == null) {
				stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
				return null;
			}
			stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(apply), time, timeUnit);
			unLock(id);
			return apply;
		} else {
			try {
				Thread.sleep(30);
				return getWithMutex(prefix, id, type, dbFallback, time, timeUnit);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * 逻辑过期解决缓存击穿
	 */
	public <T, ID> T getWithLogicalExpire(String prefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time,
			TimeUnit timeUnit) {
		String key = prefix + id;
		String json = stringRedisTemplate.opsForValue().get(key);

		if (StrUtil.isBlank(json)) {
			return null;
		}

		RedisData<T> redisData = JSONObject.parseObject(json, new TypeReference<RedisData<T>>() {
		});

		// 数据未过期--直接返回
		if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
			// T data = redisData.getData();
			// if (data instanceof JSONObject) {
			// return JSONObject.parseObject(data, type);
			// }
			return redisData.getData();
		}

		// 数据过期 重建缓存 返回旧数据
		if (tryLock(id)) {
			try {
				CACHE_REBUILD_EXECUTOR.submit(() -> {
					T apply = dbFallback.apply(id);
					redisData.setData(apply);
					redisData.setExpireTime(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL));

					stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
					return apply;
				});
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				unLock(id);
			}
		}
		return redisData.getData();
	}

	/**
	 * 获取锁 使用setnx 如果有人操作 则写入失败 返回false
	 */
	private <ID> boolean tryLock(ID id) {
		Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1", 3,
				TimeUnit.SECONDS);
		return BooleanUtil.isTrue(b);
	}

	/**
	 * 操作完后 删除数据 释放锁
	 */
	private <ID> void unLock(ID id) {
		stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
	}

}
