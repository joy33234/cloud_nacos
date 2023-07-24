package com.seektop.fund.payment.dashengpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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

class DaShengScript {

    private static final Logger log = LoggerFactory.getLogger(DaShengScript.class)

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
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "203"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "205"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            payType = "207"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            payType = "209"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>()
            DataContentParms.put("oid_partner", payment.getMerchantCode())
            DataContentParms.put("pay_type", payType)
            DataContentParms.put("user_id", req.getUserId() + "")
            DataContentParms.put("sign_type", "MD5")
            DataContentParms.put("name_goods", "CZ")
            DataContentParms.put("no_order", req.getOrderId())
            DataContentParms.put("time_order", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
            DataContentParms.put("money_order", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            DataContentParms.put("notify_url", payment.getNotifyUrl() + merchant.getId())

            String toSign = MD5.toAscii(DataContentParms) + payment.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("DaShengScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.post(payment.getPayUrl() + "/gateway/bankgateway/pay", DataContentParms, 10L, requestHeader)
            log.info("DaShengScript_Prepare_resStr:{}", restr)

            JSONObject json = JSON.parseObject(restr);
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("ret_code") != "0000"
                    || StringUtils.isEmpty(json.getString("redirect_url"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }

            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(json.getString("redirect_url"))
            result.setThirdOrderId(json.getString("oid_partner"))
        } catch (Exception e) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败")
            return
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("DaShengScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("no_order")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("oid_partner", account.getMerchantCode())
        DataContentParms.put("sign_type", "MD5")
        DataContentParms.put("no_order", orderId)

        String toSign = MD5.toAscii(DataContentParms) + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("DaShengScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/check/checkorder/checkorderresult", DataContentParms, 10L, requestHeader)
        log.info("DaShengScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        //支付完成：1   等待付款：2    支付关闭：3。
        if (json != null && "1" == json.getString("ret_code")) {// 0: 处理中   1：成功    2：失败
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("money_order").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            pay.setRsp("{\"ret_code\":\"0000\",\"ret_msg\":\"\"}")
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
        DataContentParms.put("oid_partner", merchantAccount.getMerchantCode())
        DataContentParms.put("no_order", req.getOrderId())
        DataContentParms.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        DataContentParms.put("acct_name", req.getName())
        DataContentParms.put("bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("card_no", req.getCardNo())
        DataContentParms.put("time_order", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
        DataContentParms.put("money_order", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("sign_type", "MD5")


        String toSign = MD5.toAscii(DataContentParms) + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("DaShengScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/gateway/pay", DataContentParms, 10L, requestHeader)
        log.info("DaShengScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0000" != json.getString("ret_code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("ret_msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("oid_paybill"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("DaShengScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("no_order")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
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
        DataContentParms.put("oid_partner", merchant.getMerchantCode())
        DataContentParms.put("no_order", orderId)
        DataContentParms.put("sign_type", "MD5")
        DataContentParms.put("time_order", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(DataContentParms) + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("DaShengScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/gateway/pay/queryOrder", DataContentParms, 10L, requestHeader)
        log.info("DaShengScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        //SUCCESS 付款成功 PROCESSING 付款处理中 CANCEL 退款
        if ("0000" == json.getString("ret_code")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(json.getString("oid_paybill"))
            if (json.getString("result_pay") == "SUCCESS") {
                notify.setStatus(0)
                notify.setRsp("success")
            } else if (json.getString("result_pay") == "CANCEL") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("oid_partner", merchantAccount.getMerchantCode())
        DataContentParms.put("time_order", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        DataContentParms.put("sign_type", "MD5")

        String toSign = MD5.toAscii(DataContentParms) + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("DaShengScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/gateway/pay/queryAmount", DataContentParms, 10L, requestHeader)
        log.info("DaShengScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null) {
            return json.getBigDecimal("money") == null ? BigDecimal.ZERO : json.getBigDecimal("money")
        }
        return BigDecimal.ZERO
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
        return 0
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