package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.common.redis.RedisIdWorker;
import com.hmdp.common.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 内部类,获取阻塞队列的订单信息，创建订单
    private class VoucherOrderHandler implements Runnable {
        // 执行线程任务
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),  // GROUP g1 c1
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),  // COUNT 1 BLOCK 2000
                            StreamOffset.create(RedisConstants.QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    // 判断消息获取是否成功
                    if (recordList == null || recordList.isEmpty()){
                        // 失败，代表没有消息，循环
                        continue;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = recordList.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 成功，有消息，创建订单
                    handleVoucherOrder(voucherOrder);
                    // ack确认 SCAK streams.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(RedisConstants.QUEUE_NAME, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 获取pending-list队列的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),  // GROUP g1 c1
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),  // COUNT 1 BLOCK 2000
                            StreamOffset.create(RedisConstants.QUEUE_NAME, ReadOffset.from("0"))
                    );
                    // 判断消息获取是否成功
                    if (recordList == null || recordList.isEmpty()){
                        // 失败，代表没有消息，结束
                        break;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = recordList.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 成功，有消息，创建订单
                    handleVoucherOrder(voucherOrder);
                    // ack确认 SCAK streams.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(RedisConstants.QUEUE_NAME, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    // 创建订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁 集群情况下的一人一单问题
        boolean isLock = lock.tryLock();
        if(!isLock) {
            // 获取锁失败，返回错误或重试
            log.error("亲已经买过了，哒咩得死!");
            return;
        }
        try {
            // 获取代理对象（事务）
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Long setKillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = BaseContext.getCurrentUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 判断
        if (r != 0) {
            // 不得0，证明买不了
            throw new RuntimeException(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return orderId;
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 一人一单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 用户已购买
            log.error("亲已经买过了，哒咩得死!");
            return;
        }

        // +乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 这里！！！where id = ? and stock > 0
                .update();

        if (!success) {
            log.error("已抢光，红豆泥私密马赛！！！");
            return;
        }

        save(voucherOrder);
    }
}
