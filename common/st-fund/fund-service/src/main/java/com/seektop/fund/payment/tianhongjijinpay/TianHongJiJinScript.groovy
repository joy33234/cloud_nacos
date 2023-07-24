package com.seektop.fund.payment.tianhongjijinpay

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

class TianHongJiJinScript {

    private static final Logger log = LoggerFactory.getLogger(TianHongJiJinScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merch_id", account.getMerchantCode())
        DataContentParms.put("product", "802")
        DataContentParms.put("order_id", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("notify_url", account.getNotifyUrl() + merchant.getId())

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("TianHongJiJinScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId().toString(), account.getChannelName())
        String restr = okHttpUtil.post(account.getPayUrl() + "/pay/index.php/trade/pay", DataContentParms, requestHeader)
        log.info("TianHongJiJinScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "success") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(json.getString("pay_url"))
    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("TianHongJiJinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("order_id")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid)
        } else {
            return null
        }
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merch_id", account.getMerchantCode())
        DataContentParms.put("order_id", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("TianHongJiJinScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId().toString(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/index.php/trade/payQuery", DataContentParms, requestHeader)
        log.info("TianHongJiJinScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "success") {
            return null
        }
        //订单状态   未支付：unpay 成功：succ 失败：fail 处理中：handling
        if ("succ" == json.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("out_order_no"))
            return pay
        }
        return null
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("merch_id", merchantAccount.getMerchantCode())
        DataContentParms.put("order_id", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("bank_user_no", req.getCardNo())
        DataContentParms.put("bank_user_name", req.getName())
        DataContentParms.put("province", "上海市")
        DataContentParms.put("city", "上海市")
        DataContentParms.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParms.put("product", "806")

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("TianHongJiJinScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId().toString(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/index.php/trade/df", DataContentParms, requestHeader)
        log.info("TianHongJiJinScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "success" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>

        log.info("TianHongJiJinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("order_id")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merch_id", merchant.getMerchantCode())
        DataContentParms.put("order_id", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))


        log.info("TianHongJiJinScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId().toString(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/pay/index.php/trade/dfQuery", DataContentParms, requestHeader)
        log.info("TianHongJiJinScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "success" != json.getString("code")) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(json.getString("order_id"))
        notify.setThirdOrderId(json.getString("out_order_no"))
        //        订单支付状态以此为准 成功：succ  失败：fail  处理中：handling
        if (json.getString("status") == "succ") {
            notify.setStatus(0)
        } else if (json.getString("status") == "fail") {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("merch_id", merchantAccount.getMerchantCode())
        DataContentParms.put("product", "806")

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("TianHongJiJinScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId().toString(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/index.php/trade/balanceQuery", DataContentParms, requestHeader)
        log.info("TianHongJiJinScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "success" == json.getString("code")) {
            return json.getBigDecimal("df_balance") == null ? BigDecimal.ZERO : json.getBigDecimal("df_balance")
        }
        return BigDecimal.ZERO
    }

    void cancel(Object[] args) throws GlobalException {

    }


    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, String channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId)
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
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
}