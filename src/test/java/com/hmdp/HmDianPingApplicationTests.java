package com.hmdp;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

}
