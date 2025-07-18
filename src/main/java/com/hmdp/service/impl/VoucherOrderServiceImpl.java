package com.hmdp.service.impl;

import java.time.LocalDateTime;

import org.springframework.aop.framework.AopContext;
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
		synchronized (userId.toString().intern()) { // 根据值锁住当前用户，根据获取的id创建锁
			// 获取事务代理对象
			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
			return proxy.createVoucherOrder(voucherId);
		}

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
