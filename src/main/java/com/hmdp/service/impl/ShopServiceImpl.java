package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺
     * @param id
     * @return
     */
    @Override
    public Shop queryShopById(Long id) {
        //Redis查询商铺
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);
        if(MapUtil.isNotEmpty(shopMap)){
            //有数据
            if (shopMap.containsKey("__NULL__")) { // 检查空值标记
                return null; // 返回空值，避免穿透
            }
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }

        //通过数据库查询
        Shop shop = query().eq("id", id).one();
        if(shop != null){
            Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    //字段检查会在忽略空值前执行 所以不能直接 （k, v）-> v.toString()不然会抛异常
                    .setFieldValueEditor((k, v) -> v == null ? null : v.toString()));

            stringRedisTemplate.opsForHash().putAll(shopKey, map);

            stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }else {
            // 数据库不存在的数据：写入特殊空值标记（防止缓存穿透）
            Map<String, String> nullMap = new HashMap<>();
            nullMap.put("__NULL__", "1"); // 特殊空值标记

            stringRedisTemplate.opsForHash().putAll(shopKey, nullMap);
            // 设置较短的过期时间（例如5分钟）
            stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        }

        return shop;
    }

    @Transactional
    @Override
    public boolean updateShop(Shop shop) {
        if(shop.getId() == null){
            return false;
        }

        //先更新数据库
        updateById(shop);
        //再删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return true;
    }
}
