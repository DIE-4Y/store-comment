package com.hmdp.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

	private final IUserService userService;

	private final StringRedisTemplate stringRedisTemplate;

	private final String Follow_KEY = "follow:";

	/**
	 * 关注或取关
	 * 
	 * @param followUserId
	 *            关注的用户id
	 * @param isFollow
	 *            是否关注
	 * @author chenshanquan
	 * @date 2025/9/17 12:57
	 * @return com.hmdp.dto.Result
	 **/
	@Override
	public Result follow(Long followUserId, Boolean isFollow) {
		// 获取当前用户
		UserDTO user = UserHolder.getUser();
		Long userId = user.getId();

		// 判断用户是否关注
		if (BooleanUtil.isTrue(isFollow)) {
			// 保存到数据库
			Follow follow = new Follow();
			follow.setUserId(userId).setFollowUserId(followUserId);
			boolean success = save(follow);
			if (success) {
				stringRedisTemplate.opsForSet().add(Follow_KEY + userId, followUserId.toString());
			}
			// 保存到redis
		} else {
			// 进行取关，将对应数据从数据库中移除
			LambdaQueryWrapper<Follow> Wrapper = new LambdaQueryWrapper<>();
			Wrapper.eq(Follow::getFollowUserId, followUserId);
			boolean success = remove(Wrapper);
			if (success) {
				stringRedisTemplate.opsForSet().remove(Follow_KEY + userId, followUserId.toString());
			}
		}
		return Result.ok();
	}

	/**
	 * 是否关注
	 * 
	 * @param followUserId
	 *            关注的用户id
	 * @author chenshanquan
	 * @date 2025/9/17 12:57
	 * @return com.hmdp.dto.Result
	 **/
	@Override
	public Result isFollow(Long followUserId) {
		UserDTO user = UserHolder.getUser();
		Long userId = user.getId();
		// 查询是否关注
		LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId);
		int count = count(wrapper);
		return Result.ok(count > 0);
	}

	/**
	 * 获取共同关注
	 * 
	 * @param followUserId
	 * @author chenshanquan
	 * @date 2025/9/17 16:53
	 * @return com.hmdp.dto.Result
	 **/
	@Override
	public Result followCommons(Long followUserId) {
		// 当前用户
		Long userId = UserHolder.getUser().getId();
		String key1 = Follow_KEY + userId;
		String key2 = Follow_KEY + followUserId;
		// 对当前用户和关注用户进行redis的交集查询
		Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
		// 转成list集合查询
		if (intersect == null || intersect.isEmpty()) {
			return Result.ok(Collections.emptyList());
		}
		List<Long> userIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
		List<User> users = userService.listByIds(userIds);
		List<UserDTO> userDTOS = BeanUtil.copyToList(users, UserDTO.class);
		return Result.ok(userDTOS);
	}
}
