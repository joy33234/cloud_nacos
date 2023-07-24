package com.seektop.fund.payment.jinhuifu

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.business.recharge.GlRechargeBusiness
import com.seektop.fund.mapper.GlWithdrawMapper
import com.seektop.fund.model.*
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 金汇富支付
 */

public class JinHuiFuScript {

    private static final Logger log = LoggerFactory.getLogger(JinHuiFuScript.class)

    private static final String SERVER_PAY_URL = "/v4/pay"

    private static final String SERVER_QUERY_URL = "/v4/query"

    private GlWithdrawMapper glWithdrawMapper

    private OkHttpUtil okHttpUtil

    private GlRechargeBusiness rechargeBusiness

    private GlPaymentChannelBankBusiness paymentChannelBankBusiness

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String payType
        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "3"
            } else {
                payType = "4"
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "1"
            } else {
                payType = "2"
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "5"
            } else {
                payType = "6"
            }
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "11"
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            payType = "10"
        } else {
            payType = "5"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareToScan(merchant, account, req, result, payType)
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> paramsMap = new TreeMap<>()
        paramsMap.put("amount", req.getAmount().toString())
        paramsMap.put("channel", payType)
        paramsMap.put("httpurl", account.getNotifyUrl() + merchant.getId())
        paramsMap.put("merchant_code", account.getMerchantCode())
        paramsMap.put("notifyurl", account.getNotifyUrl() + merchant.getId())
        paramsMap.put("orderid", req.getOrderId().toLowerCase())
        paramsMap.put("reference", "reference/attach")
        paramsMap.put("timestamp", (System.currentTimeMillis() / 1000) + "")
        String toSign = MD5.toAscii(paramsMap)
        toSign += "&" + account.getPrivateKey()
        paramsMap.put("sign", MD5.md5(toSign))
        log.info("JinHuiFuScript_prepareToScan_params:{}", JSON.toJSONString(paramsMap))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, paramsMap, requestHeader)
        log.info("JinHuiFuScript_prepareToScan_resp:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if (!json.getBoolean("status")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }
        JSONObject data = json.getJSONObject("data")
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(data.getString("return"))
        result.setThirdOrderId(data.getString("transaction_id"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("JinHuiFuScrip_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId.toUpperCase(), args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.rechargeBusiness = BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        GlRecharge glRecharge = rechargeBusiness.findById(orderId)
        if (glRecharge != null) {
            Map<String, String> paramsMap = new TreeMap<>()
            paramsMap.put("merchant_code", account.getMerchantCode())
            paramsMap.put("orderid", orderId)
            paramsMap.put("amount", glRecharge.getAmount().toString())
            paramsMap.put("timestamp", (System.currentTimeMillis() / 1000) + "")
            String toSign = MD5.toAscii(paramsMap)
            toSign += "&" + account.getPrivateKey()
            paramsMap.put("sign", MD5.md5(toSign))
            log.info("JinHuiFuScrip_query_params:{}", JSON.toJSONString(paramsMap))
            GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_QUERY_URL, paramsMap, requestHeader)
            log.info("JinHuiFuScrip_query_resp:{}", resStr)
            JSONObject json = JSONObject.parseObject(resStr)
            if ("PAID" == (json.getString("status")) || "SUCCESS" == (json.getString("status"))) {
                RechargeNotify pay = new RechargeNotify()
                pay.setAmount(glRecharge.getAmount())
                pay.setFee(BigDecimal.ZERO)
                pay.setOrderId(orderId.toUpperCase())
                pay.setThirdOrderId(json.getString("transid"))
                pay.setRsp("ok")
                return pay
            }
        }
        return null
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.paymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("merchant_code", merchantAccount.getMerchantCode())
        params.put("order_id", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("name", req.getName())
        params.put("bank", paymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        params.put("branch", "Shanghai")
        params.put("accountnumber", req.getCardNo())
        params.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("timestamp", (System.currentTimeMillis() / 1000) + "")

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFuScrip_Transfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/withdraw", params, requestHeader)
        log.info("JinHuiFuScrip_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != (json.getString("status"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("withdawal_ids"))
        return result
    }

    //回调有两步  第一步是批准待出款  状态：APPROVED    第二步是已完成出款 状态：DISPENSED
    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JinHuiFuScrip_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_id")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glWithdrawMapper = BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId)
        if (glWithdraw == null) {
            return null
        }
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchant_code", merchant.getMerchantCode())
        params.put("order_id", orderId)

        String toSign = MD5.toAscii(params) + "&" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFuScrip_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/v4/merchant/withdraw/query", params, requestHeader)
        log.info("JinHuiFuScrip_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("true" == (json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data")
            JSONObject dataJson = dataArr.getJSONObject(0)
            notify.setAmount(glWithdraw.getAmount().subtract(glWithdraw.getFee()))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //"REJECTED/APPROVED/PENDING/DISPENSED //拒绝/批准待出款/待支付/已完成出款    商户返回出款状态：0成功，1失败,2处理中
            if (dataJson.getString("status").equalsIgnoreCase("DISPENSED")) {
                notify.setStatus(0)
            } else if (dataJson.getString("status").equalsIgnoreCase("REJECTED")) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> params = new HashMap<>()
        params.put("merchant_code", merchantAccount.getMerchantCode())
        params.put("time", (System.currentTimeMillis() / 1000) + "")

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("JinHuiFuScrip_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/balance", params, requestHeader)
        log.info("JinHuiFuScrip_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "true" == (json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data")
            JSONObject dataJson = dataArr.getJSONObject(0)
            if (dataJson != null) {
                return dataJson.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJson.getBigDecimal("balance")
            }
        }
        return BigDecimal.ZERO
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

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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