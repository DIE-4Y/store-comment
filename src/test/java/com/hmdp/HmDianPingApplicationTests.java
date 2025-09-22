package com.hmdp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HmDianPingApplication.class)
public class HmDianPingApplicationTests {

	@Autowired
	private ShopServiceImpl shopService;
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	@Autowired
	private RedisIdWorker redisIdWorker;
	private ExecutorService es = Executors.newFixedThreadPool(500);

	/**
	 * 数据预热
	 */
	@Test
	public void logicalExpireTest() {
		Long id = 1L;
		String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
		Shop shop = shopService.getById(id);
		RedisData<Shop> shopRedisData = new RedisData<>();
		shopRedisData.setData(shop);
		shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL));
		String json = JSONUtil.toJsonStr(shopRedisData);
		stringRedisTemplate.opsForValue().set(shopKey, json);
		log.info("数据预热完成,正在取出数据");
		json = stringRedisTemplate.opsForValue().get(shopKey);
		RedisData redisData = JSONUtil.toBean(json, RedisData.class);
		log.info("数据取出成功,{}", redisData);
	}

	@Test
	public void IdWorkerTest() {
		Runnable task = () -> {
			for (int i = 0; i < 300; i++) {
				System.out.println(redisIdWorker.nextId("order"));
			}
		};
		for (int i = 0; i < 100; i++) {
			es.submit(task);
		}
	}

	/**
	 * 将数据库中的数据装入redis
	 * 
	 * @param
	 * @author chenshanquan
	 * @date 2025/9/18
	 * @return void
	 **/
	@Test
	public void loadShopData() {
		// 获取数据库中的店铺信息
		List<Shop> shops = shopService.list();

		// 数据转为以类型id为键的map，
		Map<Long, List<Shop>> shopMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));

		// 将数据存入redis
		for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
			Long typeId = entry.getKey();
			String key = RedisConstants.SHOP_GEO_KEY + typeId;
			List<Shop> value = entry.getValue();

			List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(value.size());
			for (Shop shop : value) {
				RedisGeoCommands.GeoLocation<String> geoLocation = new RedisGeoCommands.GeoLocation<String>(
						shop.getId().toString(), new Point(shop.getX(), shop.getY()));
				geoLocations.add(geoLocation);
			}
			stringRedisTemplate.opsForGeo().add(key, geoLocations);
		}
	}

	@Test
	public void testHyperLog() {
		String[] strings = new String[1000];
		for (int i = 0; i < 1000000; i++) {
			// 每一千条插入
			int j = i % 1000;
			strings[j] = "user_" + i;
			if (j == 999) {
				stringRedisTemplate.opsForHyperLogLog().add("hl2", strings);
			}
		}
		Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
		System.out.println(hl2);
	}
}
