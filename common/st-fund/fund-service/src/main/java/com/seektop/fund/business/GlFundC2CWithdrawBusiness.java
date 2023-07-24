package com.seektop.fund.business;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.vo.WithdrawVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
public class GlFundC2CWithdrawBusiness {

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private RedisService redisService;

    /**
     * 提现内部回调
     *
     * @param withdrawOrderId   提现订单号
     * @param rechargeOrderId   充值订单号
     * @param status            1：待付款 2：待确认到账  3：成功   4：付款超时   5：收款超时 6：付款撤销
     * @return
     * @throws GlobalException
     */
    public String withdrawNotify(String withdrawOrderId, String rechargeOrderId, Integer status) throws GlobalException {
        log.info("c2c_withdraw_notify_withdrawOrderId:{},rechargeOrderId:{},status:{}",withdrawOrderId, rechargeOrderId, status);
        Date now = new Date();
        GlWithdraw glWithdraw = glWithdrawBusiness.findById(withdrawOrderId);
        if (ObjectUtils.isEmpty(glWithdraw)) {
            return "faild";
        }
        if (status.equals(1)) {
            glWithdraw.setStatus(FundConstant.WithdrawStatus.RECHARGE_PENDING);
            glWithdraw.setThirdOrderId(rechargeOrderId);
            glWithdraw.setLastUpdate(now);
            glWithdraw.setRemark("代付申请成功");
            glWithdrawBusiness.updateByPrimaryKeySelective(glWithdraw);
            return "success";
        } else if (status.equals(2)) {
            glWithdraw.setStatus(FundConstant.WithdrawStatus.CONFIRM_PENDING);
            glWithdraw.setThirdOrderId(rechargeOrderId);
            glWithdraw.setLastUpdate(now);
            glWithdrawBusiness.updateByPrimaryKeySelective(glWithdraw);
            //设置提现收款过期TTL
            setTTL(glWithdraw);
            return "success";
        } else if (status.equals(3)) {
            WithdrawVO withdrawVO = DtoUtils.transformBean(glWithdraw, WithdrawVO.class);
            withdrawVO.setStatus(status);
            withdrawVO.setThirdOrderId(rechargeOrderId);
            WithdrawNotify notify = glWithdrawBusiness.notify(withdrawVO, Collections.emptyList());
            if (notify != null && notify.getStatus() != 2 && "success".equals(notify.getRsp())) {
                return "success";
            }
        } else if (status.equals(4)) {
            glWithdraw.setLastUpdate(now);
            String currentRejectReason = "充值用户付款超时：" + rechargeOrderId;
            glWithdraw.setRejectReason(currentRejectReason);
            glWithdraw.setRemark(currentRejectReason);
            glWithdraw.setStatus(FundConstant.WithdrawStatus.AUTO_PENDING);
            glWithdraw.setThirdOrderId(null);
            glWithdrawBusiness.updateByPrimaryKey(glWithdraw);
            return "success";
        } else if (status.equals(5)) {
            glWithdraw.setStatus(FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT);
            glWithdraw.setThirdOrderId(rechargeOrderId);
            glWithdraw.setLastUpdate(now);
            glWithdrawBusiness.updateByPrimaryKeySelective(glWithdraw);
            return "success";
        } else if (status.equals(6)) {
            glWithdraw.setStatus(FundConstant.WithdrawStatus.AUTO_PENDING);
            glWithdraw.setRemark("待撮合");
            glWithdraw.setThirdOrderId(null);
            glWithdraw.setLastUpdate(now);
            glWithdrawBusiness.updateByPrimaryKey(glWithdraw);
            return "success";
        }
        return "faild";
    }


    private void setTTL(GlWithdraw withdraw) {
        try {
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            String key = String.format(KeyConstant.C2C.C2C_WITHDRAW_TTL,withdraw.getOrderId());
            redisService.set(key,"ttl",configDO.getWithdrawReceiveConfirmAlertTimeout() * 60);
        } catch (Exception e) {
            log.error("极速提现订单设置过期ttl异常", e);
        }
    }

}
