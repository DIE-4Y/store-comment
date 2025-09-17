package com.hmdp.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

	private final IUserService userService;
	private final IFollowService followService;
	private final StringRedisTemplate stringRedisTemplate;

	private static final String BLOG_LIKED_KEY = "blog:liked:";
	private final String FEED_KEY = "feed:";
	// 滚动查询一页的条数
	private final int PAGE_SIZE = 2;

	/**
	 * 查询热点数据
	 * 
	 * @param current
	 *            当前页码
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/16 9:53
	 **/
	@Override
	public Result queryHotBlog(Integer current) {
		// 根据用户查询
		Page<Blog> page = query().orderByDesc("liked").page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
		// 获取当前页数据
		List<Blog> records = page.getRecords();
		// 查询用户
		records.forEach(blog -> {
			setUserByBlog(blog);
			setLikedFlagByBlog(blog);
		});
		return Result.ok(records);
	}

	/**
	 * 根据id查询博客信息
	 * 
	 * @param id
	 *            博客id
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/16 9:57
	 **/
	@Override
	public Result queryBlogById(Long id) {
		Blog blog = getById(id);
		if (blog == null) {
			return Result.fail("笔记不存在");
		}
		// 设置博客的用户相关信息
		setUserByBlog(blog);
		setLikedFlagByBlog(blog);
		return Result.ok(blog);
	}

	/**
	 * 博客点赞
	 * 
	 * @param id
	 *            博客id
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/16 12:00
	 **/
	@Override
	public Result likeBlog(Long id) {
		// 获取用户信息
		UserDTO user = UserHolder.getUser();
		// 判断用户是否点过赞
		Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, user.getId().toString());

		if (score != null) {
			// 点过赞 点赞数-1，将用户从set中移除
			boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
			if (success) {
				stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, user.getId().toString());
			}
		} else {
			// 没点过点赞 点赞数+1，将该用户加入set
			boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
			if (success) {
				stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, user.getId().toString(),
						System.currentTimeMillis());
			}
		}

		return Result.ok();
	}

	/**
	 * 查询点赞前5名，按时间排序
	 * 
	 * @param id
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/16 11:56
	 **/
	@Override
	public Result queryBlogLikes(Long id) {
		Set<String> result = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
		// 如果没有数据，说明没人点赞返回空列表
		if (result == null || result.isEmpty()) {
			return Result.ok(Collections.emptyList());
		}
		// 将数据转为Long列表
		List<Long> ids = result.stream().map(Long::valueOf).collect(Collectors.toList());
		String idsStr = StrUtil.join(",", ids);
		// 根据数据查询点赞人
		List<User> users = userService.lambdaQuery().select(User::getId, User::getIcon, User::getNickName)
				// 根据ids查询
				.in(User::getId, ids)
				// 结尾指定返回数据的顺序
				.last(" ORDER BY FIELD(id," + idsStr + ")").list();
		// users转为UserDTO返回去除敏感信息
		List<UserDTO> userDTOS = BeanUtil.copyToList(users, UserDTO.class);
		return Result.ok(userDTOS);
	}

	@Override
	public Result saveBlog(Blog blog) {
		// 获取登录用户
		UserDTO user = UserHolder.getUser();
		blog.setUserId(user.getId());
		// 保存探店博文
		boolean success = save(blog);
		if (!success) {
			return Result.fail("发布笔记失败");
		}
		// 查询粉丝
		List<Follow> follows = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
		for (Follow follow : follows) {
			// 获取粉丝id
			Long followId = follow.getUserId();
			// 推送到feed
			String key = FEED_KEY + followId;
			stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
		}

		// 返回id
		return Result.ok(blog.getId());
	}

	/**
	 * 滚动分页
	 * 
	 * @param max
	 * @param offset
	 * @return com.hmdp.dto.Result
	 * @author chenshanquan
	 * @date 2025/9/17 21:32
	 **/
	@Override
	public Result queryBlogOfFollow(Long max, Integer offset) {
		// 获取当前用户
		Long userId = UserHolder.getUser().getId();
		// 查询收件箱
		String key = FEED_KEY + userId;
		Set<ZSetOperations.TypedTuple<String>> result = stringRedisTemplate.opsForZSet()
				// 查询语句：ZREVRANGEBYSCORE key Max Min LIMIT offset count
				.reverseRangeByScoreWithScores(key, 0, max, offset, PAGE_SIZE);

		if (result == null || result.isEmpty()) {
			return Result.ok(Collections.emptyList());
		}

		// 返回的偏移量，用于下一次查询
		int os = 1;

		// id列表
		List<Long> ids = new ArrayList<>(result.size());
		long minTime = 0;

		// 解析数据：blogId, minTime, offset
		for (ZSetOperations.TypedTuple<String> tuple : result) {
			// 获取id
			Long id = Long.valueOf(tuple.getValue());
			ids.add(id);
			long time = tuple.getScore().longValue();
			if (time == minTime) {
				++os;
			} else {
				minTime = time;
				os = 1;
			}
		}

		// 查询blog数据
		String idsStr = StrUtil.join(",", ids);
		List<Blog> blogs = lambdaQuery().in(Blog::getId, ids)
				// 按顺序获取blog
				.last(" ORDER BY FIELD(id," + idsStr + ")").list();
		// 设置博客的用户相关信息
		for (Blog blog : blogs) {
			setUserByBlog(blog);
			setLikedFlagByBlog(blog);
		}

		// 封装并返回
		ScrollResult scrollResult = new ScrollResult();
		scrollResult.setList(blogs);
		scrollResult.setOffset(os);
		scrollResult.setMinTime(minTime);

		return Result.ok(scrollResult);
	}

	/**
	 * 设置是用户否点过赞
	 * 
	 * @param blog
	 *            博客
	 * @return void
	 * @author chenshanquan
	 * @date 2025/9/16 10:27
	 **/
	private void setLikedFlagByBlog(Blog blog) {
		// 获取用户信息
		UserDTO user = UserHolder.getUser();
		if (user == null) {
			return;
		}
		Long id = blog.getId();
		// 判断用户是否点过赞
		Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, user.getId().toString());
		blog.setIsLike(score != null);
	}

	/**
	 * 设置博客的用户信息
	 * 
	 * @param blog
	 *            博客
	 * @return void
	 * @author chenshanquan
	 * @date 2025/9/16 10:13
	 **/
	private void setUserByBlog(Blog blog) {
		Long userId = blog.getUserId();
		User user = userService.getById(userId);
		blog.setName(user.getNickName());
		blog.setIcon(user.getIcon());
	}
}
