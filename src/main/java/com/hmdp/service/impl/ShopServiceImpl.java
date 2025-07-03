package com.hmdp.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

	private final StringRedisTemplate stringRedisTemplate;
	// 线程池
	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(1);

	/**
	 * 根据id查询商铺
	 */
	@Override
	public Shop queryShopById(Long id) {
		// 互斥锁解决缓存击穿
		// return queryShopByIdMutex(id);
		// 逻辑过期解决缓存击穿
		return queryShopByIdLogicalExpire(id);
	}

	/**
	 * 互斥实现查询防止缓存击穿
	 */
	public Shop queryShopByIdMutex(Long id) {
		// Redis查询商铺
		String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
		Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);

		if (MapUtil.isNotEmpty(shopMap)) {
			// 有数据
			if (shopMap.containsKey("__NULL__")) { // 检查空值标记
				return null; // 返回空值，避免穿透
			}
			return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
		}

		// 没查到 互斥写入
		if (tryLock(id)) {
			// 通过数据库查询
			Shop shop = query().eq("id", id).one();
			if (shop != null) {
				Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>(),
						CopyOptions.create().setIgnoreNullValue(true)
								// 字段检查会在忽略空值前执行 所以不能直接 （k, v）-> v.toString()不然会抛异常
								.setFieldValueEditor((k, v) -> v == null ? null : v.toString()));

				stringRedisTemplate.opsForHash().putAll(shopKey, map);

				stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
			} else {
				// 数据库不存在的数据：写入特殊空值标记
				Map<String, String> nullMap = new HashMap<>();
				nullMap.put("__NULL__", "1"); // 特殊空值标记

				stringRedisTemplate.opsForHash().putAll(shopKey, nullMap);
				// 设置较短的过期时间
				stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
			}
			return shop;

		} else {

			// 有人写 休眠30ms
			try {
				Thread.sleep(30);
				return queryShopByIdMutex(id);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * 热点数据采用逻辑过期解决缓存击穿--需要提前存入数据 不要用hash存储 也不要使用有泛型的对象 容易出错
	 */
	public Shop queryShopByIdLogicalExpire(Long id) {
		// Redis查询商铺
		String shopKey = RedisConstants.CACHE_SHOP_KEY + id;

		// Redis获取JSON 数据
		String json = stringRedisTemplate.opsForValue().get(shopKey);
		if (StrUtil.isBlank(json)) {
			return null;
		}

		// 反序列化为 RedisData<Shop>
		RedisData<Shop> redisData = JSONObject.parseObject(json, new TypeReference<RedisData<Shop>>() {
		});

		if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
			return redisData.getData();
		}

		// 没查到过期了 写入时进行逻辑过期
		if (tryLock(id)) {
			// 创建线程进行写入
			CACHE_REBUILD_EXECUTOR.submit(() -> {
				// 数据库查询
				try {
					Thread.sleep(200);
					Shop shop = getById(id);
					redisData.setData(shop);
					redisData.setExpireTime(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL));
					String jsonStr = JSONUtil.toJsonStr(redisData);
					stringRedisTemplate.opsForValue().set(shopKey, jsonStr);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					// 线程写入完成释放锁
					unLock(id);
				}
			});
		}
		// 未获取到锁 直接返回过期数据
		return redisData.getData();
	}

	@Transactional
	@Override
	public boolean updateShop(Shop shop) {
		if (shop.getId() == null) {
			return false;
		}

		// 先更新数据库
		updateById(shop);
		// 再删除缓存
		stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

		return true;
	}

	/**
	 * 获取锁 使用setnx 如果有人操作 则写入失败 返回false
	 * 
	 * @param id
	 * @return
	 */
	private boolean tryLock(Long id) {
		Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1", 3,
				TimeUnit.SECONDS);
		return BooleanUtil.isTrue(b);
	}

	/**
	 * 操作完后 删除数据 释放锁
	 * 
	 * @param id
	 */
	private void unLock(Long id) {
		stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY);
	}
}
