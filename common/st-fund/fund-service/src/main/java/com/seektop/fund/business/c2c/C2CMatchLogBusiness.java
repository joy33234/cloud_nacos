package com.seektop.fund.business.c2c;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.C2CMatchLogTypeEnum;
import com.seektop.fund.mapper.C2CMatchLogMapper;
import com.seektop.fund.model.C2CMatchLog;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2CMatchLogBusiness extends AbstractBusiness<C2CMatchLog> {

    private final C2CMatchLogMapper c2CMatchLogMapper;

    public PageInfo<C2CMatchLog> findPage(String orderId, C2CMatchLogTypeEnum logTypeEnum, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(C2CMatchLog.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("orderId", orderId);
        if (logTypeEnum != null) {
            criteria.andEqualTo("type", logTypeEnum.getType());
        }
        condition.setOrderByClause(" create_date asc");
        return new PageInfo<>(findByCondition(condition));
    }

    public Date getCreateDate(String orderId, C2CMatchLogTypeEnum typeEnum) {
        return c2CMatchLogMapper.getLogCreateDate(orderId, typeEnum.getType());
    }

    public Date getCreateDate(String orderId, String linkedOrderId, C2CMatchLogTypeEnum typeEnum) {
        return c2CMatchLogMapper.getLogCreateDateWithLinkedOrderId(orderId, linkedOrderId, typeEnum.getType());
    }

    public void lockLog(String orderId, GlUserDO userDO, BigDecimal amount) {
        C2CMatchLog matchLog = new C2CMatchLog();
        matchLog.setOrderId(orderId);
        matchLog.setType(C2CMatchLogTypeEnum.MATCH_LOCK.getType());
        matchLog.setContent(userDO.getUsername() + "请求充值" + amount + "，匹配成功，更新为锁定状态");
        matchLog.setCreateDate(new Date());
        save(matchLog);
    }

    public void unLockLog(String orderId) {
        C2CMatchLog matchLog = new C2CMatchLog();
        matchLog.setOrderId(orderId);
        matchLog.setType(C2CMatchLogTypeEnum.MATCH_UNLOCK.getType());
        matchLog.setContent("撮合订单延时自动解除锁定状态");
        matchLog.setCreateDate(new Date());
        save(matchLog);
    }

    public void matchedLog(C2CWithdrawOrderPool order, GlUserDO userDO, BigDecimal amount) {
        C2CMatchLog withdrawOrderMatchLog = new C2CMatchLog();
        withdrawOrderMatchLog.setOrderId(order.getWithdrawOrderId());
        withdrawOrderMatchLog.setLinkedOrderId(order.getRechargeOrderId());
        withdrawOrderMatchLog.setType(C2CMatchLogTypeEnum.MATCHED.getType());
        String withdrawOrderLogContent = userDO.getUsername() + "充值" + amount + "，创建订单" + order.getRechargeOrderId() + "成功，提现订单更新为待付款状态";
        withdrawOrderMatchLog.setContent(withdrawOrderLogContent);
        withdrawOrderMatchLog.setCreateDate(new Date());
        save(withdrawOrderMatchLog);

        C2CMatchLog rechargeOrderMatchLog = new C2CMatchLog();
        rechargeOrderMatchLog.setOrderId(order.getRechargeOrderId());
        rechargeOrderMatchLog.setLinkedOrderId(order.getWithdrawOrderId());
        rechargeOrderMatchLog.setType(C2CMatchLogTypeEnum.MATCHED.getType());
        String rechargeOrderLogContent = userDO.getUsername() + "充值" + amount + "，撮合提现订单" + order.getWithdrawOrderId() + "成功";
        rechargeOrderMatchLog.setContent(rechargeOrderLogContent);
        rechargeOrderMatchLog.setCreateDate(new Date());
        save(rechargeOrderMatchLog);
    }

    public void withdrawOrderCreateLog(String withdrawOrderId, GlUserDO userDO, BigDecimal amount) {
        C2CMatchLog matchLog = new C2CMatchLog();
        matchLog.setOrderId(withdrawOrderId);
        matchLog.setType(C2CMatchLogTypeEnum.WITHDRAW_CREATE.getType());
        matchLog.setContent(userDO.getUsername() + "提现" + amount + "，进入撮合池等待匹配");
        matchLog.setCreateDate(new Date());
        save(matchLog);
    }

    public void rechargeOrderCancelLog(C2CWithdrawOrderPool order, Integer userId, String username) {
        C2CMatchLog withdrawOrderMatchLog = new C2CMatchLog();
        withdrawOrderMatchLog.setOrderId(order.getWithdrawOrderId());
        withdrawOrderMatchLog.setLinkedOrderId(order.getRechargeOrderId());
        withdrawOrderMatchLog.setType(C2CMatchLogTypeEnum.RECHARGE_CANCEL.getType());
        String withdrawOrderLogContent = username + "撤销充值订单" + order.getRechargeOrderId() + "，提现订单回归待撮合状态";
        withdrawOrderMatchLog.setContent(withdrawOrderLogContent);
        withdrawOrderMatchLog.setCreateDate(new Date());
        save(withdrawOrderMatchLog);

        C2CMatchLog rechargeOrderMatchLog = new C2CMatchLog();
        rechargeOrderMatchLog.setOrderId(order.getRechargeOrderId());
        rechargeOrderMatchLog.setLinkedOrderId(order.getWithdrawOrderId());
        rechargeOrderMatchLog.setType(C2CMatchLogTypeEnum.RECHARGE_CANCEL.getType());
        String rechargeOrderLogContent = username + "撤销充值订单，与撮合的提现订单" + order.getWithdrawOrderId() + "匹配解除";
        rechargeOrderMatchLog.setContent(rechargeOrderLogContent);
        rechargeOrderMatchLog.setCreateDate(new Date());
        save(rechargeOrderMatchLog);
    }

    public void withdrawOrderCancelLog(C2CWithdrawOrderPool order) {
        C2CMatchLog withdrawOrderMatchLog = new C2CMatchLog();
        withdrawOrderMatchLog.setOrderId(order.getWithdrawOrderId());
        withdrawOrderMatchLog.setLinkedOrderId(order.getRechargeOrderId());
        withdrawOrderMatchLog.setType(C2CMatchLogTypeEnum.WITHDRAW_CANCEL.getType());
        String withdrawOrderLogContent = order.getUsername() + "的提现订单取消，从撮合池中移除";
        withdrawOrderMatchLog.setContent(withdrawOrderLogContent);
        withdrawOrderMatchLog.setCreateDate(new Date());
        save(withdrawOrderMatchLog);
    }

}