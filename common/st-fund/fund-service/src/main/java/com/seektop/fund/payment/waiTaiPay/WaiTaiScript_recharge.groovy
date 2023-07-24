package com.seektop.fund.payment.waiTaiPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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

import java.math.RoundingMode
/**
 * @desc 万泰支付
 * @date 2021-10-26
 * @auth otto
 */
public class WaiTaiScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(WaiTaiScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private final String PAY_URL = "/Pay_Index.html"
    private final String QUERY_URL = "/Pay_Trade_query.html"

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String gateway = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "1" //卡转卡
        }

        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, Object> params = new LinkedHashMap<>()
        params.put("pay_memberid", account.getMerchantCode())
        params.put("pay_orderid", req.getOrderId())
        params.put("pay_applydate", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("pay_bankcode", gateway) //通道編碼
        params.put("pay_notifyurl", account.getNotifyUrl() + merchant.getId())
        params.put("pay_callbackurl", account.getNotifyUrl())
        params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        String data_sign = MD5.md5(toSign).toUpperCase()
        params.put("pay_md5sign", data_sign)
        params.put("pay_productname", "recharge")
        params.put("format", "JSON")
        params.put("fkrname", req.getFromCardUserName())

        log.info("WaiTaiScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("WaiTaiScript_recharge_prepare_resp:{} , orderId: {}", resStr , req.getOrderId() )

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("api接口异常，稍后重试")
            return
        }

        if ("success" != json.getString("status")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        BankInfo bankInfo = new BankInfo()
        bankInfo.setBankId(-1)
        bankInfo.setBankName(json.getString("bank_name"))
        bankInfo.setCardNo(json.getString("bank_card_number"))
        bankInfo.setName(json.getString("name"))

        result.setThirdOrderId(json.getString("trans_order_no"))
        result.setBankInfo(bankInfo)

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("WaiTaiScript_notify_resp:{}", JSON.toJSONString(resMap))

        Map<String, String> signMap = new HashMap();
        signMap.put("memberid",resMap.get("memberid"))
        signMap.put("orderid",resMap.get("orderid"))
        signMap.put("amount",resMap.get("amount"))
        signMap.put("realamount",resMap.get("realamount"))
        signMap.put("transaction_id",resMap.get("transaction_id"))
        signMap.put("datetime",resMap.get("datetime"))
        signMap.put("returncode",resMap.get("returncode"))
        String thirdSign = resMap.get("sign"); ;

        String toSign = MD5.toAscii(signMap) + "&key=" + account.getPrivateKey();
        toSign = MD5.md5(toSign).toUpperCase();

        if (StringUtils.isNotEmpty(resMap.get("orderid")) && toSign == thirdSign) {
            return this.payQuery(okHttpUtil, account, resMap.get("orderid"))
        }

        log.info("WaiTaiScript_notify_Sign: 回调资料错误或验签失败，单号：{}", resMap.get("orderid"))
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("pay_memberid", account.getMerchantCode())
        params.put("pay_orderid", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("pay_md5sign", MD5.md5(toSign).toUpperCase())

        //进程已结束，退出代码为 0
        log.info("WaiTaiScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("WaiTaiScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "00" != json.getString("returncode")) {
            return null
        }

        // NOTPAY-未支付 SUCCESS已支付
        if ("SUCCESS" == (json.getString("trade_state"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("realamount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("OK")
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