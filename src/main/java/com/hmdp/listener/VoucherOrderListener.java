package com.hmdp.listener;

import com.hmdp.common.constant.RabbitMqConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VoucherOrderListener {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = RabbitMqConstants.VOUCHER_ORDER_QUEUE, containerFactory = "customContainerFactory")
    public void listenVoucherOrderSuccess(VoucherOrder voucherOrder){
        log.info("监听下单队列：{}", voucherOrder);
        voucherOrderService.handleVoucherOrder(voucherOrder);
    }
}
