package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {
        Set<String> rangeList = stringRedisTemplate.opsForZSet().range("cache:list", 0, 10);
        if(rangeList != null && !rangeList.isEmpty()){
            return rangeList.stream()
                    .map(i -> JSONUtil.toBean(i, ShopType.class))
                    .sorted(Comparator.comparingInt(ShopType::getSort))
                    .collect(Collectors.toList());
        }
        List<ShopType> sort = query().orderByAsc("sort").list();

        if(sort == null || sort.isEmpty()){
            throw new RuntimeException("没有商家类型数据");
        }

        stringRedisTemplate.opsForZSet().add("cache:list", sort.stream()
                .map(i -> new DefaultTypedTuple<>(JSONUtil.toJsonStr(i), (double) i.getSort()))
                .collect(Collectors.toSet()));

        return sort;
    }
}
