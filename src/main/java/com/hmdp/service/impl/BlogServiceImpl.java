package com.hmdp.service.impl;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

	private final UserServiceImpl userService;
	private final StringRedisTemplate stringRedisTemplate;

	private static final String BLOG_LIKED_KEY = "blog:liked:";

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
	 * @author chenshanquan
	 * @date 2025/9/16 9:57
	 * @return com.hmdp.dto.Result
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

	@Override
	public Result likeBlog(Long id) {
		// 获取用户信息
		UserDTO user = UserHolder.getUser();

		// 判断用户是否点过赞
		Boolean member = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, user.getId().toString());

		if (BooleanUtil.isTrue(member)) {
			// 点过赞 点赞数-1，将用户从set中移除
			boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
			if (success) {
				stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id, user.getId().toString());
			}
		} else {
			// 没点过点赞 点赞数+1，将该用户加入set
			boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
			if (success) {
				stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id, user.getId().toString());
			}
		}

		return Result.ok();
	}

	/**
	 * 设置是用户否点过赞
	 * 
	 * @param blog
	 *            博客
	 * @author chenshanquan
	 * @date 2025/9/16 10:27
	 * @return void
	 **/
	private void setLikedFlagByBlog(Blog blog) {
		// 获取用户信息
		UserDTO user = UserHolder.getUser();
		Long id = blog.getId();
		// 判断用户是否点过赞
		Boolean member = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, user.getId().toString());
		blog.setIsLike(BooleanUtil.isTrue(member));
	}

	/**
	 * 设置博客的用户信息
	 * 
	 * @param blog
	 *            博客
	 * @author chenshanquan
	 * @date 2025/9/16 10:13
	 * @return void
	 **/
	private void setUserByBlog(Blog blog) {
		Long userId = blog.getUserId();
		User user = userService.getById(userId);
		blog.setName(user.getNickName());
		blog.setIcon(user.getIcon());
	}
}
