package com.hmdp.controller;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

	@Resource
	private IFollowService followService;

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
	@PutMapping("/{id}/{isFollow}")
	public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
		return followService.follow(followUserId, isFollow);
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
	@GetMapping("/or/not/{id}")
	public Result isFollow(@PathVariable("id") Long followUserId) {
		return followService.isFollow(followUserId);
	}
}
