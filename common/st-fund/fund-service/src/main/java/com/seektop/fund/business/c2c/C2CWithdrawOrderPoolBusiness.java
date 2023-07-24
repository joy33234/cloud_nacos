package com.seektop.fund.business.c2c;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.C2CMatchLogTypeEnum;
import com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundC2CRechargeBusiness;
import com.seektop.fund.business.GlFundC2CWithdrawBusiness;
import com.seektop.fund.mapper.C2CMatchLogMapper;
import com.seektop.fund.mapper.C2CWithdrawOrderPoolMapper;
import com.seektop.fund.model.C2CMatchLog;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2CWithdrawOrderPoolBusiness extends AbstractBusiness<C2CWithdrawOrderPool> {

    private final DynamicKey dynamicKey;

    private final C2CMatchLogMapper c2CMatchLogMapper;
    private final C2CWithdrawOrderPoolMapper c2CWithdrawOrderPoolMapper;

    private final GlFundC2CRechargeBusiness glFundC2CRechargeBusiness;
    private final GlFundC2CWithdrawBusiness glFundC2CWithdrawBusiness;

    @Transactional(rollbackFor = GlobalException.class)
    public void confirmPayment(C2CWithdrawOrderPool order, GlUserDO userDO) throws GlobalException {
        try {
            C2CWithdrawOrderPool updateOrder = new C2CWithdrawOrderPool();
            updateOrder.setWithdrawOrderId(order.getWithdrawOrderId());
            updateOrder.setPaymentDate(new Date());
            updateOrder.setStatus(C2CWithdrawOrderStatusEnum.WAIT_CONFIRM_RECEIVE.getStatus());
            updateByPrimaryKeySelective(updateOrder);
            // 更新提现订单的撮合日志
            C2CMatchLog withdrawOrderMatchLog = new C2CMatchLog();
            withdrawOrderMatchLog.setOrderId(order.getWithdrawOrderId());
            withdrawOrderMatchLog.setLinkedOrderId(order.getRechargeOrderId());
            withdrawOrderMatchLog.setType(C2CMatchLogTypeEnum.CONFIRM_PAYMENT.getType());
            withdrawOrderMatchLog.setContent("用户" + userDO.getUsername() + "操作充值订单" + order.getRechargeOrderId() + "确认付款，提现订单变更为待确认收款状态");
            withdrawOrderMatchLog.setCreateDate(new Date());
            c2CMatchLogMapper.insert(withdrawOrderMatchLog);
            // 更新充值订单的撮合日志
            C2CMatchLog rechargeOrderMatchLog = new C2CMatchLog();
            rechargeOrderMatchLog.setOrderId(order.getRechargeOrderId());
            rechargeOrderMatchLog.setLinkedOrderId(order.getWithdrawOrderId());
            rechargeOrderMatchLog.setType(C2CMatchLogTypeEnum.CONFIRM_PAYMENT.getType());
            rechargeOrderMatchLog.setContent("用户" + userDO.getUsername() + "操作充值订单确认付款，提现订单" + order.getWithdrawOrderId() + "变更为待确认收款状态");
            rechargeOrderMatchLog.setCreateDate(new Date());
            c2CMatchLogMapper.insert(rechargeOrderMatchLog);

            // 回调充值订单进入待确认到账状态
            glFundC2CRechargeBusiness.rechargeNotify(order.getRechargeOrderId(), "2");
            // 回调提现订单进入待确认到账状态
            glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), order.getRechargeOrderId(), 2);
        } catch (Exception ex) {
            log.error("提现{} 充值{}确认付款发生异常", order.getWithdrawOrderId(), order.getRechargeOrderId(), ex);
            throw new GlobalException("撮合订单" + order.getWithdrawOrderId() + " - " + order.getRechargeOrderId() + "确认付款发生异常");
        }
    }

    @Transactional(rollbackFor = GlobalException.class)
    public void confirmReceive(C2CWithdrawOrderPool order, GlUserDO userDO) throws GlobalException {
        try {
            C2CWithdrawOrderPool updateOrder = new C2CWithdrawOrderPool();
            updateOrder.setWithdrawOrderId(order.getWithdrawOrderId());
            updateOrder.setReceiveDate(new Date());
            updateOrder.setStatus(C2CWithdrawOrderStatusEnum.SUCCESS.getStatus());
            if (ObjectUtils.isEmpty(order.getPaymentDate())) {
                updateOrder.setPaymentDate(updateOrder.getReceiveDate());
            }
            updateByPrimaryKeySelective(updateOrder);
            // 更新提现订单的撮合日志
            C2CMatchLog withdrawOrderMatchLog = new C2CMatchLog();
            withdrawOrderMatchLog.setOrderId(order.getWithdrawOrderId());
            withdrawOrderMatchLog.setLinkedOrderId(order.getRechargeOrderId());
            withdrawOrderMatchLog.setType(C2CMatchLogTypeEnum.CONFIRM_RECEIVE.getType());
            withdrawOrderMatchLog.setContent("用户" + userDO.getUsername() + "操作提现订单确认收款，充值订单" + order.getRechargeOrderId() + "到账成功");
            withdrawOrderMatchLog.setCreateDate(new Date());
            c2CMatchLogMapper.insert(withdrawOrderMatchLog);
            // 更新充值订单的撮合日志
            C2CMatchLog rechargeOrderMatchLog = new C2CMatchLog();
            rechargeOrderMatchLog.setOrderId(order.getRechargeOrderId());
            rechargeOrderMatchLog.setLinkedOrderId(order.getWithdrawOrderId());
            rechargeOrderMatchLog.setType(C2CMatchLogTypeEnum.CONFIRM_RECEIVE.getType());
            rechargeOrderMatchLog.setContent("用户" + userDO.getUsername() + "操作提现订单" + order.getWithdrawOrderId() + "确认收款，到账成功");
            rechargeOrderMatchLog.setCreateDate(new Date());
            c2CMatchLogMapper.insert(rechargeOrderMatchLog);
            // 回调充值和提现订单状态成功
            glFundC2CRechargeBusiness.rechargeNotify(order.getRechargeOrderId(), "3");
            glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), order.getRechargeOrderId(), 3);
        } catch (Exception ex) {
            log.error("提现{} 充值{}确认收款发生异常", order.getWithdrawOrderId(), order.getRechargeOrderId(), ex);
            throw new GlobalException("撮合订单" + order.getWithdrawOrderId() + " - " + order.getRechargeOrderId() + "确认收款发生异常");
        }
    }

    public C2CWithdrawOrderPool getByRechargeOrderId(String rechargeOrderId) {
        C2CWithdrawOrderPool order = new C2CWithdrawOrderPool();
        order.setRechargeOrderId(rechargeOrderId);
        return c2CWithdrawOrderPoolMapper.selectOne(order);
    }

    public List<C2CWithdrawOrderPool> matchWithdrawOrder(BigDecimal amount, Integer userId, String ip) {
        JSONObject configObj = dynamicKey.getDynamicValue(DynamicKey.Key.C2C_CONFIG, JSONObject.class);
        return c2CWithdrawOrderPoolMapper.matchWithdrawOrder(amount, userId, ip, configObj.getBoolean("sameIpMatch"));
    }

    public List<C2CWithdrawOrderPool> matchWithdrawOrderByLessThan(BigDecimal amount, Integer userId, String ip) {
        JSONObject configObj = dynamicKey.getDynamicValue(DynamicKey.Key.C2C_CONFIG, JSONObject.class);
        return c2CWithdrawOrderPoolMapper.matchWithdrawOrderByLessThan(amount, userId, ip, configObj.getBoolean("sameIpMatch"));
    }

    public void updateLocked(String withdrawOrderId, GlUserDO userDO) {
        C2CWithdrawOrderPool order = new C2CWithdrawOrderPool();
        order.setWithdrawOrderId(withdrawOrderId);
        order.setIsLocked(true);
        order.setLockedDate(new Date());
        order.setLockedUserId(userDO.getId());
        updateByPrimaryKeySelective(order);
    }

    public void updateUnlock(String withdrawOrderId) {
        C2CWithdrawOrderPool order = findById(withdrawOrderId);
        if (ObjectUtils.isEmpty(order)) {
            return;
        }
        order.setWithdrawOrderId(withdrawOrderId);
        order.setIsLocked(false);
        order.setLockedDate(null);
        order.setLockedUserId(null);
        updateByPrimaryKey(order);
    }

    public void updateProgress(String withdrawOrderId, String rechargeOrderId, Integer userId) {
        C2CWithdrawOrderPool order = new C2CWithdrawOrderPool();
        order.setWithdrawOrderId(withdrawOrderId);
        order.setRechargeOrderId(rechargeOrderId);
        order.setIsLocked(true);
        order.setLockedUserId(userId);
        order.setLockedDate(new Date());
        order.setMatchedDate(new Date());
        order.setStatus(C2CWithdrawOrderStatusEnum.WAIT_PAYMENT.getStatus());
        log.info("定位撮合成功删除提现订单_撮合成功更新状态:{}", JSON.toJSONString(order));
        updateByPrimaryKeySelective(order);
    }

    public void updateCancel(String withdrawOrderId) {
        C2CWithdrawOrderPool order = findById(withdrawOrderId);
        order.setWithdrawOrderId(withdrawOrderId);
        order.setRechargeOrderId(null);
        order.setIsLocked(false);
        order.setLockedUserId(null);
        order.setLockedDate(null);
        order.setMatchedDate(null);
        order.setPaymentDate(null);
        order.setReceiveDate(null);
        order.setStatus(C2CWithdrawOrderStatusEnum.PENDING.getStatus());
        updateByPrimaryKey(order);
    }

}