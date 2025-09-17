package com.hmdp.service.impl;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;

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
}
