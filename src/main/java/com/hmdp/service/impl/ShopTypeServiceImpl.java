package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;
    
    @Override
    public List<ShopType> queryShopTypeList() {
        //先从redis查询
        List<ShopType> shopTypeList = new ArrayList<>();
        List<String> shopTypes = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0L, -1L);
        //不为空添加返回
        if (shopTypes != null && !shopTypes.isEmpty()){
            for (String shopType : shopTypes) {
                shopTypeList.add(JSONUtil.toBean(shopType, ShopType.class));
            }
            return shopTypeList;
        }

        //从数据库获取
        shopTypeList = query().orderByDesc("update_time").list();

        //存入redis
        shopTypes = new ArrayList<>();
        for (ShopType shopType : shopTypeList) {
            shopTypes.add(JSONUtil.toJsonPrettyStr(shopType));
        }
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, shopTypes);
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return shopTypeList;
    }
}
