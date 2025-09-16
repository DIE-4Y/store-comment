package com.hmdp.controller;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/shop-type")
@CrossOrigin
public class ShopTypeController {
	@Resource
	private IShopTypeService typeService;

	@GetMapping("/list")
	public Result queryTypeList() {
		log.info("查询店铺列表");
		List<ShopType> typeList = typeService.queryShopTypeList();
		return Result.ok(typeList);
	}
}
