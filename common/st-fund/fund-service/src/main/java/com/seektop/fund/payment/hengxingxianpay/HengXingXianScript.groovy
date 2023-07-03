package com.seektop.fund.payment.hengxingxianpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.HtmlTemplateUtils
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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 恒星线支付
 *
 */

class HengXingXianScript {

    private static final Logger log = LoggerFactory.getLogger(HengXingXianScript.class)

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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String payType = ""
        if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            result.setRedirectUrl("https://www.google.com")
            return
        }
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "1001"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "1002"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            payType = "3001"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType, args[5])
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType, Object[] args) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>()
            DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            DataContentParms.put("merchantNum", payment.getMerchantCode())
            DataContentParms.put("payWayType", payType)
            DataContentParms.put("merchantOrderNum", req.getOrderId())
            String toSign = MD5.toAscii(DataContentParms) + "&key=" + payment.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))
            DataContentParms.put("callbackUrl", payment.getNotifyUrl() + merchant.getId())

            log.info("HengXIngxianScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            String restr = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/api/merchant-api/api-recharge/request", DataContentParms);
            log.info("HengXIngxianScript_Prepare_resStr:{}", restr)
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setMessage(restr)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("HengXIngxianScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("merchantOrderNum")
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
        DataContentParms.put("merchantNum", account.getMerchantCode())
        DataContentParms.put("merchantOrderNum", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("HengXIngxianScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        String payUrl = account.getPayUrl() + "/api/merchant-api/api-recharge/get"
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(payUrl, JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengXIngxianScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        //1待支付，2已完成，3已撤单。
        if (dataJSON != null && "2" == dataJSON.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("receiveAmount").setScale(0, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            pay.setRsp("SUCCESS")
            return pay
        }
        return null
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("merchantNum", account.getMerchantCode())
        DataContentParms.put("merchantOrderNum", req.getOrderId())
        DataContentParms.put("userCardNum", req.getCardNo())
        DataContentParms.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))


        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.put("callbackUrl", account.getNotifyUrl() + account.getMerchantId())
        DataContentParms.put("userCardName", req.getName())

        log.info("HengXIngxianScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/merchant-api/user-withdraw/request", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengXIngxianScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("HengXIngxianScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("merchantOrderNum")
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("merchantNum", merchant.getMerchantCode())
        DataContentParms.put("merchantOrderNum", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("HengXIngxianScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/merchant-api/user-withdraw/get", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengXIngxianScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || !json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(dataJSON.getString("sysOrderNum"))
            //1待处理，2进行中，3拒绝，6完成，7失败，8进行中
            if (dataJSON.getString("status") == "6") {
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == "7" || dataJSON.getString("status") == "3") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        log.info(JSON.toJSONString(notify))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("merchantNum", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))


        log.info("HengXIngxianScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/merchant-api/user-balance/get", JSON.toJSONString(DataContentParms), requestHeader)
        log.info("HengXIngxianScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && json.getBoolean("success")) {
            return json.getBigDecimal("data") == null ? BigDecimal.ZERO : json.getBigDecimal("data")
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
}
