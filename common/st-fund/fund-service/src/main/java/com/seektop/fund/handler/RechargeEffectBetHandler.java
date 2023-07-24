package com.seektop.fund.handler;

import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.model.GlRecharge;
import com.seektop.report.fund.RechargeEffectBetReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeEffectBetHandler {

    private final GlRechargeBusiness glRechargeBusiness;
    private final GlFundUserAccountBusiness glFundUserAccountBusiness;

    public void rechargeEffectBet(RechargeEffectBetReport report) throws GlobalException {
        try {
            if (ObjectUtils.isEmpty(report)) {
                throw new GlobalException("充值成功处理提现流水信息发生异常：上报的对象为空");
            }
            if (StringUtils.isEmpty(report.getRechargeOrderId())) {
                throw new GlobalException("充值成功处理提现流水信息发生异常：上报的充值订单号为空");
            }
            GlRecharge recharge = glRechargeBusiness.findById(report.getRechargeOrderId());
            if (ObjectUtils.isEmpty(recharge)) {
                throw new GlobalException("充值成功处理提现流水信息发生异常：上报的充值订单号" + report.getRechargeOrderId() + "未查询到充值记录");
            }
            boolean flg = glFundUserAccountBusiness.updateDigitalEffect(
                    recharge.getUserId(),
                    recharge.getOrderId(),
                    recharge.getCoin(),
                    recharge.getAmount(),
                    recharge.getPaymentId()
            );
            if (flg == false) {
                throw new GlobalException("充值成功处理提现流水信息发生异常：上报的充值订单号" + report.getRechargeOrderId() + "处理所需流水不成功");
            }
        } catch (Exception ex) {
            throw new GlobalException("充值成功处理提现流水信息发生异常", ex);
        }
    }

}