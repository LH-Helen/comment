package com.hmdp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopService extends IService<Shop> {

    Shop quertById(Long id) throws InterruptedException;

    void updateShop(Shop shop);

    Page<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
