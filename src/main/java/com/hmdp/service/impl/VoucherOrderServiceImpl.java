package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIDWorker redisIDWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("尚未开始");
        }
        //3.判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");
        }
        Long userId=UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, voucher);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, SeckillVoucher voucher) {
        Long userId=UserHolder.getUser().getId();
        int count=query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count>0)
        {
            return Result.fail("仅能购买一单");
        }
        //5.扣减库存
        boolean success=seckillVoucherService.update()
                .setSql("stock=stock-1").eq("voucher_id", voucherId).eq("stock", voucher.getStock())
                .update();
        if(!success)
        {
            return Result.fail("库存不足");
        }
        //6.创建订单
        VoucherOrder voucherOrder=new VoucherOrder();
        long seckill_order_id = redisIDWorker.nextId("seckill_order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(seckill_order_id);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(seckill_order_id);

    }
}
