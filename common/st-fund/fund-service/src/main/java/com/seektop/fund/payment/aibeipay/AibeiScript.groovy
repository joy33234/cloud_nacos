package com.seektop.fund.payment.aibeipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.withdraw.GlWithdrawBusiness
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

public class AibeiScript {

    private static final Logger log = LoggerFactory.getLogger(AibeiScript.class)

    private OkHttpUtil okHttpUtil

    private GlWithdrawBusiness glWithdrawBusiness

    public Map<Integer, String> bankIdCodeMap = new HashMap<Integer, String>() {
        {
            put(1, "CMB")//招商银行
            put(2, "ICBC")//中国工商银行
            put(3, "CCB")//中国建设银行
            put(4, "ABC")//中国农业银行
            put(5, "BOC")//中国银行
            put(6, "COMM")//交通银行
            put(7, "GDB")//广东发展银行
            put(8, "CEBB")//中国光大银行
            put(9, "SPDB")//上海蒲东发展银行
            put(10, "CMBC")//中国民生银行
            put(11, "SPAB")//平安银行
            put(12, "CIB")//兴业银行
            put(13, "CITIC")//中信银行
            put(14, "PSBC")//中国邮政储蓄银行
        }
    }

    public Map<Integer, String> typeMap = new HashMap<Integer, String>() {
        {
            put(FundConstant.PaymentType.UNIONPAY_SACN, "unionpay")//银联
            put(FundConstant.PaymentType.ALI_PAY, "alipaygp")
            put(FundConstant.PaymentType.WECHAT_PAY, "wechat")
            put(FundConstant.PaymentType.BANKCARD_TRANSFER, "bank")
            put(FundConstant.PaymentType.ALI_TRANSFER, "ali2bank")
        }
    }

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("不支持充值方式")
            return
        }
    }

    //银联扫码支付
    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> param = new LinkedHashMap<>()
        String keyValue = account.getPrivateKey()// 私钥
        param.put("return_type", "json")//商户号
        param.put("api_code", account.getMerchantCode())
        param.put("is_type", typeMap.get(merchant.getPaymentId()))
        param.put("price", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        param.put("order_id", req.getOrderId())
        long timeStamp = System.currentTimeMillis() / 1000
        param.put("time", String.valueOf(timeStamp))//时间戳
        param.put("mark", "CZ")
        param.put("return_url", account.getResultUrl() + merchant.getId())
        param.put("notify_url", account.getNotifyUrl() + merchant.getId())

        String signStrTemp = MD5.toAscii(param) + "&key=" + keyValue
        param.put("sign", MD5.md5(signStrTemp).toUpperCase())
        log.info("AibeiScript_prepare_params:{}", JSON.toJSONString(param))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String retBack = okHttpUtil.post((account.getPayUrl() + "/channel/Common/mail_interface"), param, requestHeader)
        log.info("AibeiScript_prepare_result:{}", retBack)

        JSONObject json = JSON.parseObject(retBack)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject message = json.getJSONObject("messages")
        if (message.getString("returncode") == ("SUCCESS")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(json.getString("payurl"))
            result.setThirdOrderId(json.getString("paysapi_id"))
            return
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(message.getString("returnmsg"))
            return
        }
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        log.info("AibeiScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String dataStr = resMap.get("reqBody")
        log.info("AibeiScript_Notify_dataStr:{}", dataStr)
        JSONObject dataJson = JSON.parseObject(dataStr)
        if (null == dataJson) {
            return null
        }
        String code = dataJson.getString("code")
        String orderId = dataJson.getString("order_id")
        String price = dataJson.getString("price")
        String isType = dataJson.getString("is_type")
        StringBuilder orderStr = new StringBuilder()
        orderStr.append(orderId).append("-").append(price).append("-").append(isType)
        if ("1" == (code)) {
            return payQuery(okHttpUtil, account, orderStr.toString(), args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        String keyValue = account.getPrivateKey()
        String[] dataStr = orderId.split("-")
        Map<String, String> param = new HashMap<>()
        String price = new BigDecimal(dataStr[1]).setScale(2, RoundingMode.DOWN).toString()
        param.put("api_code", account.getMerchantCode())
        param.put("is_type", dataStr[2])
        param.put("order_id", dataStr[0])
        param.put("price", price)
        String signStr = MD5.toAscii(param) + "&key=" + keyValue
        param.put("sign", MD5.md5(signStr).toUpperCase())
        log.info("AibeiScript_query_param: {}", param)
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resStr = okHttpUtil.post(account.getPayUrl() + "/channel/Common/query_pay", param, requestHeader)
        log.info("AibeiScript_query_resStr: {}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        if (json == null) {
            return null
        }

        String code = json.getString("code")
        if ("1" == (code)) {
            RechargeNotify notify = new RechargeNotify()
            notify.setAmount(json.getBigDecimal("price").setScale(2, RoundingMode.DOWN))
            notify.setFee(BigDecimal.ZERO)
            notify.setOrderId(dataStr[0])
            notify.setThirdOrderId(json.getString("paysapi_id"))
            return notify
        }
        return null
    }


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        Map<String, String> param = new LinkedHashMap<>()
        String keyValue = merchantAccount.getPrivateKey()// 私钥

        param.put("api_code", merchantAccount.getMerchantCode())
        param.put("order_id", req.getOrderId())
        param.put("cash_money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        long timeStamp = System.currentTimeMillis() / 1000
        param.put("time", String.valueOf(timeStamp))
        param.put("bank_code", bankIdCodeMap.get(req.getBankId()))
        param.put("bank_branch", "上海市")
        param.put("bank_account_number", req.getCardNo())
        param.put("bank_compellation", req.getName())
        param.put("t", "0")
        param.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String signStr = MD5.toAscii(param) + "&key=" + keyValue
        param.put("sign", MD5.md5(signStr).toUpperCase())

        log.info("AibeiScript_transfer_params:{}", JSON.toJSONString(param))

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/channel/Withdraw/mail_interface", param, requestHeader)
        log.info("AibeiScript_transfer_result:{}", retBack)
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(param.toString())
        result.setResData(retBack)
        JSONObject retJson = JSONObject.parseObject(retBack)
        if (null == retJson || StringUtils.isEmpty(retJson.getString("return_code"))) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if ("SUCCESS" != (retJson.getString("return_code"))) {
            result.setValid(false)
            result.setMessage(retJson.getString("return_msg"))
            return result
        }
        req.setMerchantId(merchantAccount.getMerchantId())
        result.setValid(true)
        result.setMessage(retJson.getString("return_msg"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        log.info("AibeiScript_doTransferNotify_resMap:{}", JSON.toJSONString(resMap))
        String dataStr = resMap.get("reqBody")

        JSONObject dataJson = JSON.parseObject(dataStr)
        if (null == dataJson) {
            return null
        }
        String orderId = dataJson.getString("order_id")
        if (StringUtils.isEmpty(orderId)) {
            return null
        }
        return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        String keyValue = merchant.getPrivateKey()
        Map<String, String> param = new HashMap<>()
        GlWithdraw withdraw = glWithdrawBusiness.findById(orderId)
        String price = withdraw.getAmount().setScale(2, RoundingMode.DOWN).toString()
        param.put("api_code", merchant.getMerchantCode())
        param.put("order_id", orderId)
        param.put("cash_money", price)
        String signStr = MD5.toAscii(param) + "&key=" + keyValue
        param.put("sign", MD5.md5(signStr).toUpperCase())
        log.info("AibeiScript_doTransferQuery_param: {}", param)
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String retBack = okHttpUtil.post(merchant.getPayUrl() + "/channel/Withdraw/query_pay", param, requestHeader)
        log.info("AibeiScript_doTransferQuery_resStr: {}", retBack)
        JSONObject retJson = JSON.parseObject(retBack)
        if (null == retJson) {
            return null
        }

        String message = retJson.getString("messages")
        JSONObject messageJson = JSONObject.parseObject(message)
        if (null == messageJson || "SUCCESS" != (messageJson.getString("return_code"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(retJson.getBigDecimal("cash_money"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(retJson.getString("paysapi_id"))
        if ("1" == (retJson.getString("code"))) {
            notify.setStatus(0)
        } else if ("2" == (retJson.getString("code"))) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glWithdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        String keyValue = merchantAccount.getPrivateKey()
        Map<String, String> param = new HashMap<>()
        param.put("api_code", merchantAccount.getMerchantCode())
        String signStr = MD5.toAscii(param) + "&key=" + keyValue
        param.put("sign", MD5.md5(signStr).toUpperCase())
        log.info("AibeiScript_queryBalance_param: {}", param)
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/channel/Withdraw/query_money", param, requestHeader)
        log.info("AibeiScript_queryBalance_resStr: {}", retBack)
        JSONObject retJson = JSON.parseObject(retBack)
        if (null == retJson || StringUtils.isEmpty(retJson.getString("money"))) {
            return BigDecimal.ZERO
        }
        return retJson.getBigDecimal("money")
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
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
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
}