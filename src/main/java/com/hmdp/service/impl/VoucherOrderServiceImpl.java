package com.hmdp.service.impl;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
		implements IVoucherOrderService {

	private final RedisIdWorker redisIdWorker;
	private final ISeckillVoucherService iSeckillVoucherService;
	private final StringRedisTemplate stringRedisTemplate;
	private final RedissonClient redissonClient;

	private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

	private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
	private IVoucherOrderService proxy;

	static {
		// 静态代码块初始化加载lua脚本
		SECKILL_SCRIPT = new DefaultRedisScript<>();
		SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
		SECKILL_SCRIPT.setResultType(Long.class);
	}

	/**
	 * 类初始化后执行的方法
	 * 
	 * @param
	 * @return void
	 * @author chenshanquan
	 * @date 2025/9/15 9:13
	 **/
	@PostConstruct
	private void init() {
		SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
	}

	// 异步下单任务
	private class VoucherOrderHandler implements Runnable {
		String queueName = "stream.orders";

		@Override
		public void run() {
			while (true) {
				try {
					StreamOperations<String, Object, Object> opsForStream = stringRedisTemplate.opsForStream();
					// 获取消息队列的信息
					List<MapRecord<String, Object, Object>> result = opsForStream.read(
							// 消费组和名称
							Consumer.from("g1", "c1"),
							// 设置阻塞时间2s
							StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
							// 读取第一个未读取的
							StreamOffset.create(queueName, ReadOffset.lastConsumed()));

					// 判断是否获取成功，如果获取失败进行下一次循环
					if (result == null || result.isEmpty()) {
						continue;
					}

					// 解析消息队列的订单
					MapRecord<String, Object, Object> entries = result.get(0);

					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(entries.getValue(), new VoucherOrder(), true);

					// 获取成功，下单
					handleVoucherOrder(voucherOrder);

					// ack确认从队列移除消息
					opsForStream.acknowledge(queueName, "g1", entries.getId());
				} catch (Exception e) {
					// 出现异常，未获取到待处理的订单，从pending-list中获取正在处理的订单
					handlePendingList();
				}
			}
		}

		/**
		 * 处理pending-list里的订单
		 * 
		 * @param
		 * @author chenshanquan
		 * @date 2025/9/15 15:26
		 * @return void
		 **/
		private void handlePendingList() {
			while (true) {
				try {
					StreamOperations<String, Object, Object> opsForStream = stringRedisTemplate.opsForStream();
					// 获取pending-list的信息
					List<MapRecord<String, Object, Object>> result = opsForStream.read(
							// 消费组和名称
							Consumer.from("g1", "c1"),
							// 设置阻塞时间2s
							StreamReadOptions.empty().count(1),
							// 读取第一个未读取的
							StreamOffset.create(queueName, ReadOffset.from("0")));

					// 判断是否获取成功，如果获取失败结束
					if (result == null || result.isEmpty()) {
						break;
					}

					// 解析pending-list的订单
					MapRecord<String, Object, Object> entries = result.get(0);
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(entries.getValue(), new VoucherOrder(), true);

					// 获取成功，下单
					handleVoucherOrder(voucherOrder);

					// ack确认从队列移除消息
					opsForStream.acknowledge(queueName, "g1", entries.getId());
				} catch (Exception e) {
					// 出现异常，继续循环
					log.error("处理pending-list订单异常，{}", e);
				}
			}
		}
	}

	/*
	 * private final BlockingQueue<VoucherOrder> orderTask = new
	 * ArrayBlockingQueue<>(1024 * 1024);
	 * 
	 * private class VoucherOrderHandler implements Runnable {
	 * 
	 * @Override public void run() { while (true) { try { VoucherOrder voucherOrder
	 * = orderTask.take(); handleVoucherOrder(voucherOrder); } catch (Exception e) {
	 * log.error("处理下单异常，{}", e); } } } }
	 */

	/**
	 * 下单处理器
	 * 
	 * @param voucherOrder
	 *            订单
	 * @return void
	 * @author chenshanquan
	 * @date 2025/9/15 9:16
	 **/
	private void handleVoucherOrder(VoucherOrder voucherOrder) {
		// 获取用户对象
		Long userId = voucherOrder.getUserId();
		// 创建锁对象
		RLock lock = redissonClient.getLock("lock:order:" + userId);
		boolean b = lock.tryLock();
		if (!b) {
			log.error("获取锁失败");
			return;
		}

		// 获取订单id
		Long voucherId = voucherOrder.getVoucherId();
		proxy.createVoucherOrder(voucherOrder);
	}

	/**
	 * 创建订单-V2
	 * 
	 * @param voucherOrder
	 *            订单对象
	 * @author chenshanquan
	 * @date 2025/9/15 15:36
	 * @return void
	 **/
	@Override
	@Transactional
	public void createVoucherOrder(VoucherOrder voucherOrder) {
		Long userId = voucherOrder.getUserId();
		Long voucherId = voucherOrder.getVoucherId();
		Integer count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
		if (count > 0) {
			log.error("不能重复下单");
			return;
		}

		// 扣减库存
		boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
				.gt("stock", 0).update();
		if (!success) {
			log.error("库存不足");
			return;
		}

		save(voucherOrder);
	}

	/**
	 * 抢购秒杀券-v3
	 * 
	 * @param voucherId
	 *            优惠券id
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/12 22:26
	 **/
	@Override
	public Result seckillVoucher(Long voucherId) {
		UserDTO user = UserHolder.getUser();
		long orderId = redisIdWorker.nextId("order");
		// 执行脚本
		Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
				// 空keys
				Collections.emptyList(),
				// 优惠券id
				voucherId.toString(),
				// 用户id
				user.getId().toString(),
				// 脚本新增订单id参数
				String.valueOf(orderId));
		if (result.intValue() != 0) {
			return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
		}

		// 获取代理对象
		proxy = (IVoucherOrderService) AopContext.currentProxy();

		// 返回order的id
		return Result.ok(orderId);
	}

	/**
	 * 抢购秒杀券-v2
	 * 
	 * @param voucherId
	 *            优惠券id
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/12 22:26
	 **/
	/*
	 * @Override public Result seckillVoucher(Long voucherId) { UserDTO user =
	 * UserHolder.getUser(); long orderId = redisIdWorker.nextId("order"); // 执行脚本
	 * Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
	 * Collections.emptyList(), voucherId.toString(),
	 * UserHolder.getUser().getId().toString()); if (result.intValue() != 0) {
	 * return Result.fail(result == 1 ? "库存不足" : "不能重复下单"); }
	 * 
	 * // 创建订单 VoucherOrder voucherOrder = new VoucherOrder();
	 * voucherOrder.setUserId(user.getId()).setVoucherId(voucherId).setId(
	 * redisIdWorker.nextId("order")) .setVoucherId(voucherId);
	 * 
	 * // 订单加入阻塞队列 orderTask.add(voucherOrder);
	 * 
	 * proxy = (IVoucherOrderService) AopContext.currentProxy();
	 * 
	 * // 返回order的id return Result.ok(voucherOrder.getId()); }
	 */

	/**
	 * 抢购秒杀券-v1
	 * 
	 * @param voucherId
	 *            优惠券id
	 * @author chenshanquan
	 * @date 2025/9/11 10:46
	 * @return com.hmdp.dto.Result
	 **/
	/*
	 * @Override public Result seckillVoucher(Long voucherId) {
	 * 
	 * // 1.查询优惠券 SeckillVoucher voucher =
	 * iSeckillVoucherService.getById(voucherId); if
	 * (voucher.getBeginTime().isAfter(LocalDateTime.now())) { return
	 * Result.fail("秒杀尚未开始"); }
	 * 
	 * if (voucher.getEndTime().isBefore(LocalDateTime.now())) { return
	 * Result.fail("秒杀已结束"); }
	 * 
	 * // 查看库存 if (voucher.getStock() < 1) { return Result.fail("库存不足"); }
	 * 
	 * Long userId = UserHolder.getUser().getId(); // SimpleRedisLock lock = new
	 * SimpleRedisLock("order:" + userId, // stringRedisTemplate); RLock lock =
	 * redissonClient.getLock("lock:order:" + userId); // 不指定参数默认只尝试一次，30秒后自动释放
	 * boolean success = lock.tryLock(); if (!success) { return
	 * Result.fail("请勿重复下单"); } try { IVoucherOrderService proxy =
	 * (IVoucherOrderService) AopContext.currentProxy(); return
	 * proxy.createVoucherOrder(voucherId); } finally { lock.unlock(); } //
	 * synchronized (userId.toString().intern()) { // 根据值锁住当前用户，根据获取的id创建锁 // //
	 * 获取事务代理对象 // IVoucherOrderService proxy = (IVoucherOrderService) //
	 * AopContext.currentProxy(); // return proxy.createVoucherOrder(voucherId); //
	 * }
	 * 
	 * }
	 */

	@Override
	@Transactional // spring实现 必须用代理才能实现sql同步--必须先释放锁在提交事务不然还是会超卖
	public Result createVoucherOrder(Long voucherId) {
		Long userId = UserHolder.getUser().getId();
		Integer count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
		if (count > 0) {
			return Result.fail("不能重复下单");
		}

		// 扣减库存
		boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
				.gt("stock", 0).update();
		if (!success) {
			return Result.fail("库存不足");
		}

		// 创建订单
		long orderId = redisIdWorker.nextId("order");

		// 保存并返回订单
		VoucherOrder voucherOrder = new VoucherOrder();
		voucherOrder.setVoucherId(voucherId);
		voucherOrder.setId(orderId);
		voucherOrder.setUserId(userId);
		save(voucherOrder);
		return Result.ok(orderId);
	}
}
