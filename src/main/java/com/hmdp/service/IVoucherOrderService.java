package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Long setKillVoucher(Long voucherId);

//    Long createVoucherOrder(Long voucherId);
    void createVoucherOrder(VoucherOrder voucherOrder);
}
