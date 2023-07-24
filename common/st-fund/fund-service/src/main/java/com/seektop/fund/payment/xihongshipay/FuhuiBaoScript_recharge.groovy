package com.seektop.fund.payment.xihongshipay

import com.alibaba.fastjson.JSON
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @desc
 * @auth joy
 * @date 2021-03-17
 */

public class FuhuiBaoScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(FuhuiBaoScript_recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        log.info("XianDaiJinKongScript_recharge_prepare_params = {}", JSON.toJSONString(req))

        BankInfo bankInfo = new BankInfo()
        bankInfo.setBankId(-1)
        bankInfo.setBankBranchName("广东支行")
        bankInfo.setBankName("农村信用合作社")
        bankInfo.setCardNo("123123123")
        bankInfo.setName("张彤")
        bankInfo.setKeyword("thmas")
        result.setThirdOrderId("ML" + req.getOrderId())
        result.setBankInfo(bankInfo)
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {

    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {

        return null
    }

    void cancel(Object[] args) throws GlobalException {

    }


/**
 * 是否为内部渠道
 *
 * @param args
 * @return
 */
    public boolean innerpay(Object[] args) {
        return true
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }

    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        return FundConstant.ShowType.DETAIL
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}