package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Long setKillVoucher(Long voucherId) {
        SeckillVoucher seckill = seckillVoucherService.getById(voucherId);

        if(seckill == null){
            throw new RuntimeException("优惠券不存在");
        }

        if (LocalDateTime.now().isBefore(seckill.getBeginTime())) {
            throw new RuntimeException("尚未开始");
        }

        if(LocalDateTime.now().isAfter(seckill.getEndTime())){
            throw new RuntimeException("已结束，私密马赛！");
        }

        if(seckill.getStock() < 1){
            throw new RuntimeException("已抢光，红豆泥私密马赛！！！");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized(userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Long createVoucherOrder(Long voucherId){
        Long userId = UserHolder.getUser().getId();
        // 一人一单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            // 用户已购买
            throw new RuntimeException("亲已经买过了，哒咩得死");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 这里！！！where id = ? and stock > 0
                .update();

        if(!success){
            throw new RuntimeException("已抢光，红豆泥私密马赛！！！");
        }

        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(UserHolder.getUser().getId())
                .voucherId(voucherId)
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        save(voucherOrder);
        return orderId;
    }
}
