package com.seektop.fund.payment.qinchengpay

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
 * @desc 鑫诚支付——v2
 * @date 2021-05-29
 * @auth joy
 */
public class XinchengV2Script_recharge {


    private static final Logger log = LoggerFactory.getLogger(XinchengV2Script_recharge.class)

    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/v1/pay"
    private  final String QUERY_URL =  "/v1/paystatus"

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
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            payType = "1005"//支付宝转帐，
        } else if (FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            payType = "1002"//极速支付宝
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "1001"//卡卡
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()){
            payType = "1004"//微信H5
        } else if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()){
            payType = "1004"//微信H5
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()){
            payType = "1002"
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantCode", account.getMerchantCode())
        params.put("merchantOrderNo",req.getOrderId())
        params.put("payType", payType)
        params.put("currency", "CNY")
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("callbackUrl", account.getNotifyUrl() + merchant.getId())
        params.put("clientIP", req.getIp())
        params.put("signature", MD5.md5(MD5.toAscii(params) + "&" + account.getPrivateKey()) )
        params.put("remark", "recharge")
        params.put("returnType", "1")
        params.put("redirectUrl", account.getNotifyUrl() + merchant.getId())

        log.info("XinchengScript_V2_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + PAY_URL, JSON.toJSONString(params), requestHeader)
        log.info("XinchengScript_V2_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || json.getString("status") != "1" || StringUtils.isEmpty(json.getString("data"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        result.setRedirectUrl(json.getString("data"))
        result.setThirdOrderId(json.getString("transactionNo"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("XinchengScript_V2_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject jsonObj = JSON.parseObject(resMap.get("reqBody"));

        Map<String, String> signMap  =(Map<String, String>) jsonObj;
        String thirdSign = signMap.remove("signature")
        String toSign = MD5.toAscii(signMap) + "&" + account.getPrivateKey();
        toSign = MD5.md5(toSign);

        if (StringUtils.isNotEmpty(signMap.get("merchantOrderNo")) && toSign == thirdSign) {
            return this.payQuery(okHttpUtil, account, signMap.get("merchantOrderNo"))
        }

        log.info("XinchengScript_V2_notify_Sign: 回调错误或验签失败 orderId: {} " , signMap.get("merchantOrderNo"))
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantCode", account.getMerchantCode())
        params.put("merchantOrderNo", orderId)
        params.put("signature", MD5.md5(MD5.toAscii(params) + "&" + account.getPrivateKey()))

        log.info("XinchengScript_V2_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + QUERY_URL, JSON.toJSONString(params), requestHeader)
        log.info("XinchengScript_V2_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "1") {
            return null
        }
        // 支付状态:0:尚未处理   1 : 成功, 2 : 失败, 3 : 待定
        if ("1" == json.getString("transactionStatus")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("actualPayAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("transactionNo"))
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