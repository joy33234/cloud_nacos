package com.seektop.fund.payment;

import com.seektop.constant.FundConstant;
import com.seektop.fund.business.withdraw.GlWithdrawMerchantAccountBusiness;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Slf4j
@Component
public class GlWithdrawHandlerManager {

    @Autowired
    private Map<String, GlPaymentWithdrawHandler> glPaymentWithdrawHandlerMap;

    @Autowired
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;
    /**
     * 提现handler
     *
     * @param withdrawMerchantAccount
     * @return
     */
    public GlPaymentWithdrawHandler getPaymentWithdrawHandler(GlWithdrawMerchantAccount withdrawMerchantAccount) {
        withdrawMerchantAccount = glWithdrawMerchantAccountBusiness.getWithdrawMerchant(withdrawMerchantAccount.getMerchantId());
        if (null == withdrawMerchantAccount) {
            return null;
        }
        if (Objects.equals(FUND_COMMON_ON, withdrawMerchantAccount.getEnableScript())) {
            return glPaymentWithdrawHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glPaymentWithdrawHandlerMap.get(withdrawMerchantAccount.getChannelId().toString());
        }
    }

    public String withdrawOKNotifyResponse(Integer channelId) {
        switch (channelId) {
            case FundConstant.PaymentChannel.HUIFUBAO: // 惠付宝
            case FundConstant.PaymentChannel.XINHUIPAY: //信汇支付
            case FundConstant.PaymentChannel.SITONGPAY: //四通支付
            case FundConstant.PaymentChannel.RUIFUPAY: //睿付支付
            case FundConstant.PaymentChannel.BEILEIPAY: // 蓓蕾支付
            case FundConstant.PaymentChannel.YINSHANFUPAY: // 银闪付支付
            case FundConstant.PaymentChannel.XINPAY:    //新付
            case FundConstant.PaymentChannel.FLASHPAY:  //flashpay
            case FundConstant.PaymentChannel.TTPAY:  //泰坦支付
                return "SUCCESS";
            case FundConstant.PaymentChannel.ONEGOPAY:// OneGo支付
            case FundConstant.PaymentChannel.XINDUOBAO: // 鑫多宝
            case FundConstant.PaymentChannel.CFPAY:
            case FundConstant.PaymentChannel.JINHUIFU:
            case FundConstant.PaymentChannel.ANTWITHDRAWPAY:
                return "ok";
            case FundConstant.PaymentChannel.YHPAY: // 永恒支付
            case FundConstant.PaymentChannel.UPAY:
            case FundConstant.PaymentChannel.ZHIHUIFU:
            case FundConstant.PaymentChannel.JULIPAY:
            case FundConstant.PaymentChannel.STPAYER:
                return "OK";
            case FundConstant.PaymentChannel.SAVEPAY:// 安心付
                return "opstate=0";
            case FundConstant.PaymentChannel.MACHI:
                return "200";
            default:
                return "success";
        }
    }
}
