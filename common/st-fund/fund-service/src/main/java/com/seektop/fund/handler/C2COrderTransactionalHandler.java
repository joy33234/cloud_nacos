package com.seektop.fund.handler;

import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.c2c.C2CMatchLogBusiness;
import com.seektop.fund.business.c2c.C2CWithdrawOrderPoolBusiness;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2COrderTransactionalHandler {

    private final C2CMatchLogBusiness c2CMatchLogBusiness;
    private final C2CWithdrawOrderPoolBusiness c2CWithdrawOrderPoolBusiness;

    @Transactional(rollbackFor = GlobalException.class, propagation = Propagation.REQUIRES_NEW)
    public void matchLockAndLog(String withdrawOrderId, GlUserDO userDO, BigDecimal amount) throws GlobalException {
        try {
            // 更新当前匹配到的订单为锁定状态
            c2CWithdrawOrderPoolBusiness.updateLocked(withdrawOrderId, userDO);
            // 保存订单号的撮合的日志
            c2CMatchLogBusiness.lockLog(withdrawOrderId, userDO, amount);
        } catch (Exception ex) {
            throw new GlobalException("充值订单匹配提现订单成功，更新锁定和保存撮合日志发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class, propagation = Propagation.REQUIRES_NEW)
    public void matchUnLockAndLog(String withdrawOrderId) throws GlobalException {
        try {
            // 更新为解锁状态
            c2CWithdrawOrderPoolBusiness.updateUnlock(withdrawOrderId);
            // 更新撮合日志
            c2CMatchLogBusiness.unLockLog(withdrawOrderId);
        } catch (Exception ex) {
            throw new GlobalException("充值订单匹配提现订单成功但是未创建订单，解除锁定和保存撮合日志发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class, propagation = Propagation.REQUIRES_NEW)
    public void rechargeCreateAndLog(String withdrawOrderId, String rechargeOrderId, GlUserDO userDO, BigDecimal amount) throws GlobalException {
        try {
            // 更新提现订单当前状态为待付款
            c2CWithdrawOrderPoolBusiness.updateProgress(withdrawOrderId, rechargeOrderId, userDO.getId());
            // 保存订单号的撮合的日志
            c2CMatchLogBusiness.matchedLog(c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId), userDO, amount);
        } catch (Exception ex) {
            throw new GlobalException("充值订单创建成功，更新锁定和待付款状态和保存撮合日志发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class, propagation = Propagation.REQUIRES_NEW)
    public void rechargeCancelAndLog(C2CWithdrawOrderPool order, GlUserDO userDO) throws GlobalException {
        try {
            // 更新提现订单当前状态为待付款
            c2CWithdrawOrderPoolBusiness.updateCancel(order.getWithdrawOrderId());
            // 保存订单号的撮合的日志
            c2CMatchLogBusiness.rechargeOrderCancelLog(order, userDO.getId(), userDO.getUsername());
        } catch (Exception ex) {
            throw new GlobalException("充值订单撤销，取消锁定状态和更新为待撮合状态和保存撮合日志发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class, propagation = Propagation.REQUIRES_NEW)
    public void withdrawCancelAndLog(C2CWithdrawOrderPool order) throws GlobalException {
        try {
            // 删除提现订单在撮合池中的数据
            c2CWithdrawOrderPoolBusiness.deleteById(order.getWithdrawOrderId());
            // 保存订单号的撮合的日志
            c2CMatchLogBusiness.withdrawOrderCancelLog(order);
        } catch (Exception ex) {
            throw new GlobalException("提现取消，删除撮合池中的提现订单和保存撮合日志发生异常", ex);
        }
    }

}