package com.hmdp.service.impl;

import java.time.LocalDateTime;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;

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

	/**
	 * 秒杀优惠券
	 * 
	 * @param voucherId
	 *            优惠券id
	 * @author chenshanquan
	 * @date 2025/8/17 22:27
	 * @return com.hmdp.dto.Result
	 **/
	@Override
	public Result seckillVoucher(Long voucherId) {

		// 1.查询优惠券
		SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
		if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
			return Result.fail("秒杀尚未开始");
		}

		if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
			return Result.fail("秒杀已结束");
		}

		// 查看库存
		if (voucher.getStock() < 1) {
			return Result.fail("库存不足");
		}

		Long userId = UserHolder.getUser().getId();
		// SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,
		// stringRedisTemplate);
		RLock lock = redissonClient.getLock("lock:order:" + userId);
		// 不指定参数默认只尝试一次，30秒后自动释放
		boolean success = lock.tryLock();
		if (!success) {
			return Result.fail("请勿重复下单");
		}
		try {
			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
			return proxy.createVoucherOrder(voucherId);
		} finally {
			lock.unlock();
		}
		// synchronized (userId.toString().intern()) { // 根据值锁住当前用户，根据获取的id创建锁
		// // 获取事务代理对象
		// IVoucherOrderService proxy = (IVoucherOrderService)
		// AopContext.currentProxy();
		// return proxy.createVoucherOrder(voucherId);
		// }

	}

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
