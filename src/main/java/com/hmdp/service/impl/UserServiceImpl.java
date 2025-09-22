package com.hmdp.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

	private final StringRedisTemplate stringRedisTemplate;

	private static final String DATE_FORMAT = ":yyyy-MM";

	@Override
	public Result sendCode(String phone, HttpSession session) {
		// 验证手机格式
		if (RegexUtils.isPhoneInvalid(phone)) {
			return Result.fail("手机格式错误！");
		}

		// 生成手机验证码
		String code = RandomUtil.randomNumbers(6);

		// 存入session
		// session.setAttribute("code", code);
		// session.setAttribute("phone", phone);
		// code存入redis 设置过期时间
		stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
				RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

		// 发送验证码
		log.debug("手机验证码是：" + code);

		return Result.ok();
	}

	@Override
	public Result login(LoginFormDTO loginForm, HttpSession session) {
		String phone = loginForm.getPhone();
		// 校验手机号
		if (RegexUtils.isPhoneInvalid(phone)) {
			return Result.fail("手机格式错误！");
		}

		// 校验验证码是否和手机匹配
		// Object cachePhone = session.getAttribute("phone");
		// Object cacheCode = session.getAttribute("code");
		// if(!phone.equals(cachePhone) || !loginForm.getCode().equals(cacheCode)){
		// return Result.fail("手机或验证码错误！");
		// }

		// 根据手机号获取redis里存的验证码 判断是否为空
		String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
		if (StrUtil.isBlank(cacheCode) || !cacheCode.equals(loginForm.getCode())) {
			return Result.fail("验证码错误！");
		}

		// 判断用户是否存在
		User user = query().eq("phone", phone).one();

		// 用户不存在--创建
		if (user == null) {
			user = generateUserWithPhone(phone);
		}

		// 保存用户信息到session
		// UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
		// session.setAttribute("user", userDTO);

		// UUID生成一个随机token
		UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
		String token = UUID.randomUUID().toString(true);

		// 保存用户信息到redis
		String tokenName = RedisConstants.LOGIN_USER_KEY + token;

		/*
		 * 由于使用自带的redisTemplate要求存入map的键值对都是String 需要进行设置将非String类型的转化成String类型
		 */
		Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
				CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((key, value) -> value.toString()));

		stringRedisTemplate.opsForHash().putAll(tokenName, userMap);
		stringRedisTemplate.expire(tokenName, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

		return Result.ok(token);
	}

	@Override
	public Result queryById(Long id) {
		User user = getById(id);
		if (user == null) {
			return Result.ok();
		}
		UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
		return Result.ok(userDTO);
	}

	/**
	 * 签到功能
	 */
	@Override
	public Result sign() {
		// 用户登录信息
		UserDTO user = UserHolder.getUser();
		Long userId = user.getId();

		// 当前时间
		LocalDate now = LocalDate.now();
		String date = now.format(DateTimeFormatter.ofPattern(DATE_FORMAT));

		String key = RedisConstants.USER_SIGN_KEY + userId;

		// 当前天数
		int dayOfMonth = now.getDayOfMonth();

		// 签到，当前天数比特位置1
		stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
		return Result.ok();
	}

	// 根据phone生成用户
	private User generateUserWithPhone(String phone) {
		User user = new User();
		user.setPhone(phone);
		user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
		save(user);
		return user;
	}

}
