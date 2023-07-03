package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.constant.FundConstant;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawUserBankCardMapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawAlarm;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class WithdrawAlarmBusiness extends AbstractBusiness<GlWithdrawAlarm> {

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    @Resource
    private GlWithdrawUserBankCardMapper glWithdrawUserBankCardMapper;

    public List<GlWithdrawAlarm> getAlarmList(Date startTime, Date endTime) {
        Condition condition = new Condition(GlWithdrawAlarm.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andBetween("createTime", startTime, endTime);
        return findByCondition(condition);
    }

    public GlWithdrawAlarm doWithdrawCheck(GlWithdraw withdraw) {
        GlWithdrawAlarm glWithdrawAlarm = new GlWithdrawAlarm();

        GlWithdraw originData = glWithdrawMapper.selectByPrimaryKey(withdraw.getOrderId());

        //订单状态验证
        if (null == originData || (originData.getStatus() != FundConstant.WithdrawStatus.PENDING
                && originData.getStatus() != FundConstant.WithdrawStatus.AUTO_FAILED
                && originData.getStatus() != FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT)) {
            log.error("doWithdrawApi_error_dbData:{}", withdraw.getOrderId());
            glWithdrawAlarm.setOrderId(withdraw.getOrderId());
            glWithdrawAlarm.setWithdrawCardName(withdraw.getName());
            glWithdrawAlarm.setWithdrawCardNo(withdraw.getCardNo());
            glWithdrawAlarm.setCardName(originData != null ? originData.getName() : "");
            glWithdrawAlarm.setCardNo(originData != null ? originData.getCardNo() : "");
            return glWithdrawAlarm;
        }

        //用户银行卡验证
        if (withdraw.getBankId()  != FundConstant.PaymentType.DIGITAL_PAY) {
            GlWithdrawUserBankCard userBankCard = glWithdrawUserBankCardMapper.findCardForWithdrawValid(
                    withdraw.getUserId(), 0, withdraw.getName().trim(), withdraw.getCardNo().trim());
            if (null == userBankCard) {
                log.error("doWithdrawApi_error_userBankCard:{}", withdraw.getOrderId());
                glWithdrawAlarm.setOrderId(withdraw.getOrderId());
                glWithdrawAlarm.setWithdrawCardName(withdraw.getName());
                glWithdrawAlarm.setWithdrawCardNo(withdraw.getCardNo());
                return glWithdrawAlarm;
            }
        }
        return null;
    }
}
