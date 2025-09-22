package com.hmdp.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;

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

	private final CacheUtil cacheUtil;

	/**
	 * 根据id查询商铺
	 */
	@Override
	public Shop queryShopById(Long id) {
		// 互斥锁解决缓存击穿
		// return cacheUtil.getWithMutex(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
		// this::getById,
		// RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
		// 逻辑过期解决缓存击穿
		return queryShopByIdLogicalExpire(id);
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
					// Thread.sleep(200);
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
	 * 查询附近店铺
	 * 
	 * @param typeId
	 *            店铺类型
	 * @param current
	 *            当前页
	 * @param x
	 *            经度
	 * @param y
	 *            纬度
	 * @author chenshanquan
	 * @date 2025/9/18
	 * @return com.hmdp.dto.Result
	 **/
	@Override
	public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
		// 如果没有传经纬度直接数据库分页查询
		if (x == null || y == null) {
			// 根据类型分页查询
			Page<Shop> page = lambdaQuery().eq(Shop::getTypeId, typeId)
					.page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
			// 返回数据
			return Result.ok(page.getRecords());
		}

		// 计算分页参数
		int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
		int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

		// 有经纬度，根据redis查询，设置范围为5km
		String key = RedisConstants.SHOP_GEO_KEY + typeId;
		GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
				// 当前位置
				GeoReference.fromCoordinate(x, y),
				// 搜索半径
				new Distance(5000),
				// 附加条件
				RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

		List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
		// 查询到的数据不足不用截取
		if (content.size() <= from) {
			return Result.ok(Collections.emptyList());
		}

		// 收集店铺id，店铺距离
		List<Long> ids = new ArrayList<>(DEFAULT_BATCH_SIZE);
		Map<String, Distance> map = new HashMap<>(DEFAULT_BATCH_SIZE);
		content.stream().skip(from).forEach(item -> {
			String name = item.getContent().getName();
			ids.add(Long.valueOf(name));
			Distance distance = item.getDistance();
			map.put(name, distance);
		});

		String idsStr = StrUtil.join(",", ids);

		List<Shop> shops = lambdaQuery().in(Shop::getId, ids).last(" ORDER BY FIELD(id," + idsStr + ")").list();
		for (Shop shop : shops) {
			shop.setDistance(map.get(shop.getId().toString()).getValue());
		}
		return Result.ok(shops);
	}

	/**
	 * 获取锁 使用setnx 如果有人操作 则写入失败 返回false
	 */
	private boolean tryLock(Long id) {
		Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1", 3,
				TimeUnit.SECONDS);
		return BooleanUtil.isTrue(b);
	}

	/**
	 * 操作完后 删除数据 释放锁
	 */
	private void unLock(Long id) {
		stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY);
	}
}
