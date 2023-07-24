package com.seektop.fund.payment.jiabaopay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class JiaBaoScript {

    private static final Logger log = LoggerFactory.getLogger(JiaBaoScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String channel = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            channel = "AlipayBank"
        }
        if (StringUtils.isNotEmpty(channel)) {
            prepareScan(merchant, payment, req, result, channel, args[5])
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String channel, Object[] args) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>()
            DataContentParms.put("merchantCode", payment.getMerchantCode())
            DataContentParms.put("signType", "md5")

            Map<String, String> content = new HashMap<String, String>()
            content.put("merchantCode", payment.getMerchantCode())
            content.put("merchantTradeNo", req.getOrderId())
            content.put("userId", req.getUserId().toString())
            content.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
            content.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
            //终端类型：填1 表示是电脑端，填2 表示是手机端
            if (req.getClientType() == 0) {
                content.put("terminalType", "1")
            } else {
                content.put("terminalType", "2")
            }
            content.put("channel", channel)
            content.put("extendedAttrData", "{}")
            String toSign = toAscii(content) + payment.getPrivateKey()
            content.put("sign", MD5.md5(toSign))

            DataContentParms.put("content", JSON.toJSONString(content))
            log.info("JiaBaoScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/center/deposit/apply", JSON.toJSONString(DataContentParms), requestHeader)
            log.info("JiaBaoScript_Prepare_resStr:{}", restr)

            JSONObject json = JSON.parseObject(restr);
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("status") != "SUCCESS") {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }

            JSONObject dataJSON = json.getJSONObject("data");
            JSONObject contentJSON = dataJSON.getJSONObject("content");
            if (contentJSON == null || StringUtils.isEmpty(contentJSON.getString("payUrl"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }

            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(contentJSON.getString("payUrl"))

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("JiaBaoScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        JSONObject dataJSON = json.getJSONObject("content");
        String orderid = dataJSON.getString("merchantTradeNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        } else {
            return null
        }
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantCode", account.getMerchantCode())
        DataContentParms.put("signType", "md5")

        Map<String, String> content = new HashMap<String, String>()
        content.put("merchantCode", account.getMerchantCode())
        content.put("merchantTradeNo", orderId)
        String toSign = toAscii(content) + account.getPrivateKey()
        content.put("sign", MD5.md5(toSign))

        DataContentParms.put("content", JSON.toJSONString(content))

        log.info("JiaBaoScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/pay/center/deposit/query", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("JiaBaoScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        if (json == null || json.getString("status") != "SUCCESS") {
            return null
        }

        JSONObject dataJSON = json.getJSONObject("data");
        JSONObject contentJSON = dataJSON.getJSONObject("content");
        //WAITING_PAYMENT:等待支付    PAYMENT_FAILURE:支付失败   PAYMENT_SUCCESS:支付成功
        if (contentJSON != null && "PAYMENT_SUCCESS" == contentJSON.getString("tradeStatus")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(contentJSON.getBigDecimal("amount").setScale(0, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(contentJSON.getString("tradeNo"))
            pay.setRsp("SUCCESS")
            return pay
        }
        return null
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantCode", merchantAccount.getMerchantCode())
        DataContentParms.put("signType", "md5")

        Map<String, String> content = new HashMap<String, String>()
        content.put("merchantCode", merchantAccount.getMerchantCode())
        content.put("merchantTradeNo", req.getOrderId())
        content.put("accountName", req.getName())
        content.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        content.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        content.put("bankCardNumber", req.getCardNo())
        content.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        content.put("channel", "Withdraw")
        String toSign = toAscii(content) + merchantAccount.getPrivateKey()
        content.put("sign", MD5.md5(toSign))

        DataContentParms.put("content", JSON.toJSONString(content))

        log.info("JiaBaoScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/pay/center/withdrawal/apply", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("JiaBaoScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        JSONObject dataJSON = json.getJSONObject("data");
        JSONObject contentJSON = dataJSON.getJSONObject("content");
        //WAITING_WITHDRAWAL:等待代付   WITHDRAWAL_SUCCESS:代付成功    WITHDRAWAL_FAILURE:代付失败
        if (contentJSON == null || "WITHDRAWAL_FAILURE" == contentJSON.getString("tradeStatus")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("JiaBaoScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        JSONObject dataJSON = json.getJSONObject("content");
        String orderid = dataJSON.getString("merchantTradeNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantCode", merchant.getMerchantCode())
        DataContentParms.put("signType", "md5")

        Map<String, String> content = new HashMap<String, String>()
        content.put("merchantCode", merchant.getMerchantCode())
        content.put("merchantTradeNo", orderId)
        String toSign = toAscii(content) + merchant.getPrivateKey()
        content.put("sign", MD5.md5(toSign))

        DataContentParms.put("content", JSON.toJSONString(content))

        log.info("JiaBaoScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/pay/center/withdrawal/query", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("JiaBaoScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("status")) {
            log.info("err");
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        JSONObject contentJSON = dataJSON.getJSONObject("content");
        WithdrawNotify notify = new WithdrawNotify()
        //WAITING_WITHDRAWAL:等待代付   WITHDRAWAL_SUCCESS:代付成功    WITHDRAWAL_FAILURE:代付失败
        if (contentJSON != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(contentJSON.getString("tradeNo"))
            if (contentJSON.getString("tradeStatus") == "WITHDRAWAL_SUCCESS") {
                notify.setStatus(0)
                notify.setRsp("SUCCESS ")
            } else if (contentJSON.getString("tradeStatus") == "WITHDRAWAL_FAILURE") {
                notify.setStatus(1)
                notify.setRsp("SUCCESS ")
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return BigDecimal.valueOf(-1)
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
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }


    /**
     * ASCII排序
     *
     * @param parameters
     * @return
     */
    public static String toAscii(Map<String, String> parameters) {
        List<Map.Entry<String, String>> infoIds = new ArrayList<>(parameters.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry<String, String>).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, String> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                sign.append(v)
            }
        }
        return sign.toString()
    }
}