package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.common.redis.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)))
                .build();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public void setWithNull(String key){
        stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    }

    public <T, ID> T queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<T> type,
            Function<ID, T> dbCallback,
            Long time,
            TimeUnit unit
    ) {
        String key = keyPrefix + id;
        // 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 是否存在, 存在返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        // 不存在，根据id查询数据库
        T t = dbCallback.apply(id);
        // 不存在，返回错误
        if (t == null) {
            // 将空值写入redis
            this.setWithNull(key);
            // 返回错误信息
            return null;
        }
        // 存在，写入redis
        this.set(key, t, time, unit);
        // 返回
        return t;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T,ID> T queryWithLogicalExpire(
            String keyPrefix,
            String LockKeyPrefix,
            ID id,
            Class<T> type,
            Function<ID, T> dbCallback,
            Long time,
            TimeUnit unit
    ){
        String key = keyPrefix+id;
        String LockKey = LockKeyPrefix+id;

        String json = stringRedisTemplate.opsForValue().get(key);
        // 是否存在, 不存在返回
        if (StrUtil.isBlank(json)) {
            this.setWithNull(key);
            return null;
        }
        // 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return t;
        }

        // 过期，需要缓存重建
        if (tryLock(LockKey)) {
            // 双检查
            json = stringRedisTemplate.opsForValue().get(key);

            // 是否存在, 不存在返回
            if (StrUtil.isBlank(json)) {
                this.setWithNull(key);
                return null;
            }
            // 命中，需要先把json反序列化为对象
            redisData = JSONUtil.toBean(json, RedisData.class);
            t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            // 判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期，直接返回店铺信息
                unlock(LockKey);
                return t;
            }

            // 如果还是没有，redis重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                T t1 = dbCallback.apply(id);
                this.setWithLogicalExpire(key, t1, time, unit);
                unlock(LockKey);
            });
        }
        // 返回
        return t;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
