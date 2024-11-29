package com.hmdp.service.impl;

import com.hmdp.common.constant.MessageConstants;
import com.hmdp.common.exception.ShopExistException;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.common.constant.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Shop quertById(Long id) {
        // 解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 解决缓存击穿 用逻辑过期方式
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY, id,
                Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null){
            throw new ShopExistException(MessageConstants.SHOP_NOT_FOUND);
        }
        return shop;
    }

    @Override
    @Transactional
    public void updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            throw new ShopExistException(MessageConstants.SHOP_NOT_FOUND);
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
    }
}
