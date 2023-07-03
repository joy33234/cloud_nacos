package com.seektop.fund.payment.heyuanPay

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
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc  河源支付
 * @date 2021-07-30
 * @auth joy
 */
public class HeYuanScript_recharge {


    private static final String SERVER_PAY_URL = "/pay/gateway/unify.do"

    private static final String SERVER_QUERY_URL = "/pay/gateway/query.do"


    private static final Logger log = LoggerFactory.getLogger(HeYuanScript_recharge.class)

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
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            payType = "3010"//支付宝转帐，
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "3008"//卡卡
        } else if (FundConstant.PaymentType.UNION_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            payType = "3012"//云闪付转卡
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        String[] arr = account.getMerchantCode().split("\\|\\|")
        if (arr == null || arr.length != 2) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("订单创建失败，商户未配置机构号")
            return
        }
        Map<String, String> params = new HashMap<>()
        params.put("TRDE_CODE", "20001")
        params.put("PRT_CODE", arr[1])
        params.put("VER_NO", "1.0")
        params.put("MERC_ID", arr[0])
        params.put("BIZ_CODE", payType)
        params.put("ORDER_NO", req.getOrderId())
        params.put("TXN_AMT", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("PRO_DESC", "CZ")
        params.put("NOTIFY_URL", account.getNotifyUrl() + merchant.getId())
        params.put("NON_STR", System.currentTimeSeconds().toString() + req.getOrderId())
        params.put("TM_SMP", System.currentTimeMillis().toString())
        params.put("SIGN_TYP", "MD5")

        String toSign = MD5.toAscii(params) + "&KEY=" + account.getPrivateKey()
        params.put("SIGN_DAT", MD5.md5(toSign))
        log.info("HeYuanScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSONLimitTime(account.getPayUrl() + SERVER_PAY_URL, JSON.toJSONString(params), requestHeader, 60)
        log.info("HeYuanScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if ("0000" != (json.getString("RETURNCODE")) || json.getString("RETURNCON") != ("SUCCESS")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("RETURNCON"))
            return
        }
        result.setThirdOrderId(json.getString("OUT_TRADE_NO"))
        String url = json.getString("QR_CODE")
        if (StringUtils.isNotEmpty(url)) {
            result.setRedirectUrl(url)
        } else {
            result.setMessage(json.getString("RET_HTML"))
        }
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HeYuanScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("ORDER_NO")
        } else {
            orderId = json.getString("ORDER_NO")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String[] arr = account.getMerchantCode().split("\\|\\|")
        if (arr == null || arr.length != 2) {
            return null
        }

        Map<String, String> params = new HashMap<>()
        params.put("TRDE_CODE", "20002")
        //机构号BB、ML不同
        params.put("PRT_CODE", arr[1])
        params.put("VER_NO", "1.0")
        params.put("MERC_ID",  arr[0])
        params.put("ORDER_NO", orderId)
        params.put("NON_STR", System.currentTimeMillis().toString())
        params.put("TM_SMP", System.currentTimeMillis().toString())
        params.put("SIGN_TYP", "MD5")
        String toSign = MD5.toAscii(params)
        toSign += "&KEY=" + account.getPrivateKey()
        params.put("SIGN_DAT", MD5.md5(toSign))
        log.info("HeYuanScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
//        String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader)
        String resStr = okHttpUtil.postJSONLimitTime(account.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader,60)
        log.info("HeYuanScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        // 请求成功 并且 支付成功    0-未支付  1-支付成功  2-支付失败 T-支付超时 C-处理中
        if ("0000" == (json.getString("RETURNCODE")) && "1" == (json.getString("ORD_STS"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("TXN_AMT").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("OUT_TRADE_NO"))
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
        return false
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
        return FundConstant.ShowType.NORMAL
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