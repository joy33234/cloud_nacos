package com.seektop.fund.payment.huanYu2Pay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
/**
 * @desc 寰宇支付
 * @date 2021-10-06
 * @auth Otto
 */
public class HuanYu2Script_recharge {

    private static final String SERVER_PAY_URL = "/pay/createOrder"
    private static final String SERVER_QUERY_URL = "/inquiry/payOrder"
    private static final Logger log = LoggerFactory.getLogger(HuanYu2Script_recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""

        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "BankToBank" //卡卡
        }

        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {

        Map<String, String> reqParam = new LinkedHashMap<>()
        String amount = req.getAmount().setScale(2, RoundingMode.DOWN)+""
        reqParam.put("merNo", account.getMerchantCode())
        reqParam.put("tradeNo",req.getOrderId())
        reqParam.put("cType", payType)
        reqParam.put("orderAmount",amount)
        reqParam.put("playerName", req.getFromCardUserName())
        reqParam.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        log.info("HuanYu2Script_recharge_prepare_params:{}", JSON.toJSONString(reqParam))

        String sign = MD5.md5(account.getMerchantCode() + req.getOrderId() + amount + account.getPrivateKey())
        reqParam.put("sign",sign)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, reqParam, requestHeader)
        log.info("HuanYu2Script_recharge_prepare_resp:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        //1: 成功 其餘失敗
        if (json == null || 1 != json.getInteger("Success")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(URLDecoder.decode(json.getString("Message"), "utf-8"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("Params")
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(URLDecoder.decode(dataJSON.getString("bankAccountName"), "utf-8"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(URLDecoder.decode(dataJSON.getString("bankName"), "utf-8"))
        bankInfo.setBankBranchName(URLDecoder.decode(dataJSON.getString("branchName"), "utf-8"))
        bankInfo.setCardNo(URLDecoder.decode(dataJSON.getString("bankAccount"), "utf-8"))
        result.setBankInfo(bankInfo)
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HuanYu2Script_notify_resp:{}",resMap)
        if (StringUtils.isNotEmpty(resMap.get("tradeNo"))) {
            return this.payQuery(okHttpUtil, account, resMap.get("tradeNo"), args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("tradeNo", orderId)
        params.put("merNo", account.getMerchantCode())
        params.put("sign", MD5.md5( account.getMerchantCode()+orderId+account.getPrivateKey() ));

        log.info("HuanYu2Script_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("HuanYu2Script_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || 1 != json.getInteger("Success")) {
            return null
        }

        //-1:上分失败 0:处理中 1:上分成功 3:无须上分 9:API审核
        if ( 1 == (json.getInteger("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("topupAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
            return pay

        }
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

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

}