package com.seektop.fund.payment.xiangyunpay

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 祥云支付
 */
public class XiangYunScript {


    private static final Logger log = LoggerFactory.getLogger(XiangYunScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            service = "10101"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            service = "10108"
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("不支持充值方式：" + merchant.getPaymentName())
            return
        }

        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("partner", payment.getMerchantCode())
        DataContentParms.put("service", service)
        DataContentParms.put("tradeNo", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParms.put("resultType", "json")//跳转方式  web/json  商户配置来控制
        DataContentParms.put("buyer", req.getFromCardUserName())
//        if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER
//                || merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER
//                || merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
//            DataContentParms.put("buyer", req.getFromCardUserName().trim());
//        }
        String toSign = MD5.toAscii(DataContentParms) + "&" + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XiangYunScript_Prepare_Params = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
//        String restr = okHttpUtil.post(payment.getPayUrl() + "/unionOrderVip", DataContentParms, 30, requestHeader)
        String restr = okHttpUtil.post(payment.getPayUrl() + "/unionOrder", DataContentParms, 30, requestHeader)

        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSON.parseObject(restr)
        log.info("XiangYunScript_Prepare_resStr = {}", restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if (json.getString("isSuccess").equals("T")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(json.getString("url"))
//            BankInfo bankInfo = new BankInfo()
//            bankInfo.setName(json.getString("bankCardOwner"))
//            bankInfo.setBankId(-1)
//            bankInfo.setBankName(json.getString("bankName"))
//            bankInfo.setBankBranchName(json.getString("bankName"))
//            bankInfo.setCardNo(json.getString("cardNo"))
//            result.setBankInfo(bankInfo)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("failMsg"))
        }
    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XiangYunScript_Notify_resMap = {}", JSON.toJSONString(resMap))
        String orderid = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null

    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("partner", account.getMerchantCode())
        DataContentParms.put("service", "10302")
        DataContentParms.put("outTradeNo", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XiangYunScript_Query_reqMap = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orderQuery", DataContentParms, 10L, requestHeader)
        log.info("XiangYunScript_Query_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        // 0: 处理中   1：成功    2：失败
        if ("T" == (json.getString("isSuccess")) && "1" == (json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            return pay
        }
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("partner", merchantAccount.getMerchantCode())
        DataContentParms.put("service", "10201")
        DataContentParms.put("tradeNo", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        DataContentParms.put("bankCardNo", req.getCardNo())
        DataContentParms.put("bankCardholder", req.getName())
        DataContentParms.put("subsidiaryBank", "上海市")
        DataContentParms.put("subbranch", "上海市")
        DataContentParms.put("province", "上海市")
        DataContentParms.put("city", "上海市")
        DataContentParms.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XiangYunScript_Transfer_params =  {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/agentPay", DataContentParms, 10L, requestHeader)
        log.info("XiangYunScript_Transfer_resStr = {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "T" != (json.getString("isSuccess"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        result.setThirdOrderId(json.getString("tradeId"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XiangYunScript_Notify_resMap = {}", JSON.toJSONString(resMap))
        String orderid = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("partner", merchant.getMerchantCode())
        DataContentParms.put("service", "10301")
        DataContentParms.put("outTradeNo", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchant.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XiangYunScript_TransferQuery_reqMap = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/orderQuery", DataContentParms, 10L, requestHeader)
        log.info("XiangYunScript_TransferQuery_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("T" == (json.getString("isSuccess"))) {
            notify.setAmount(json.getBigDecimal("amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(json.getString("outTradeNo"))
            notify.setThirdOrderId("")
            //订单状态判断标准： 0 处理中 1 成功 2 失败
            if (json.getString("status") == ("1")) {
                notify.setStatus(0)
            } else if (json.getString("status") == ("2")) {
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("partner", merchantAccount.getMerchantCode())
        DataContentParms.put("service", "10401")

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchantAccount.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign))

        log.info("XiangYunScript_QueryBalance_reqMap = {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balanceQuery", DataContentParms, 10L, requestHeader)
        log.info("XiangYunScript_QueryBalance_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "T" == (json.getString("isSuccess"))) {
            return json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance")
        }
        return BigDecimal.ZERO
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.XIANGYUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIANGYUN_PAY.getPaymentName())
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
//        Integer paymentId = args[1] as Integer
//        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
//                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
//                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
//            return true
//        }
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
//        Integer paymentId = args[1] as Integer
//        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
//                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
//                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
//            return true
//        }
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