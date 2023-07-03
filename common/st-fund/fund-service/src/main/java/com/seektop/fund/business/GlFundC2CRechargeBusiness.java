package com.seektop.fund.business;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.recharge.GlRechargeReceiveInfoBusiness;
import com.seektop.fund.business.recharge.GlRechargeTransactionBusiness;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.enums.UseModeEnum;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.model.GlRechargeReceiveInfo;
import com.seektop.fund.payment.RechargeNotifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class GlFundC2CRechargeBusiness {


    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;

    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;

    @Resource
    private GlRechargeReceiveInfoBusiness rechargeReceiveInfoBusiness;

    @Resource
    private GlRechargeTransactionBusiness transactionBusiness;



    /**
     * 充值内部回调
     *
     * @param orderId   充值订单号
     * @param status    1：待付款 2：待确认到账  3：成功   4：付款超时   5：收款超时
     * @return
     * @throws GlobalException
     */
    public String rechargeNotify(String orderId, String status) throws GlobalException {
        log.info("c2c_recharge_notify_orderId:{},status:{}",orderId, status);
        Date now = new Date();
        GlRecharge glRecharge = glRechargeBusiness.findById(orderId);
        GlRechargeReceiveInfo receiveInfo = rechargeReceiveInfoBusiness.findById(orderId);
        if (ObjectUtils.isEmpty(glRecharge) || ObjectUtils.isEmpty(receiveInfo)
                || ObjectUtils.isEmpty(status) || status.equals("1")) {
            return "faild";
        }

        if (status.equals("2")) {
            glRecharge.setSubStatus(FundConstant.RechargeSubStatus.RECHARGE_PENDING_CONFIRM);
            glRechargeBusiness.updateByPrimaryKeySelective(glRecharge);
            return "success";
        } else if (status.equals("3")) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("orderId", orderId);
            request.setParameter("status", status);
            request.setParameter("thirdOrderId", receiveInfo.getThirdOrderId());
            List<GlPaymentMerchantApp> merchantApps = glPaymentMerchantAppBusiness.findList(glRecharge.getPaymentId(),
                    glRecharge.getMerchantId(), null, UseModeEnum.C2C.getCode(), glRecharge.getLimitType());
            for (GlPaymentMerchantApp app:merchantApps) {
                RechargeNotifyResponse response = rechargeHandler.notify(app.getId(), request);
                if (!ObjectUtils.isEmpty(response) && response.getContent().equals("success")) {
                    return "success";
                }
            }
        } else if (status.equals("4")) {
            //超时撤销订单
            GlRechargeDO rechargeDO = DtoUtils.transformBean(glRecharge, GlRechargeDO.class);
            transactionBusiness.doRechargeTimeOut(rechargeDO);
            return "success";
        } else if (status.equals("5")) {
            glRecharge.setSubStatus(FundConstant.RechargeSubStatus.RECHARGE_UNCONFIRM);
            glRecharge.setLastUpdate(now);
            glRechargeBusiness.updateByPrimaryKeySelective(glRecharge);
            return "success";
        }
        return "faild";
    }

}
