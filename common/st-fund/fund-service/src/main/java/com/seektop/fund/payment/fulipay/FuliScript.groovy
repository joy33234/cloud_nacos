package com.seektop.fund.payment.fulipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.mapper.GlWithdrawMapper
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
import java.nio.charset.Charset

/**
 * 富力支付
 *
 * @author walter
 */
public class FuliScript {

    private static final Logger log = LoggerFactory.getLogger(FuliScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlWithdrawMapper glWithdrawMapper

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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        try {
            Map<String, String> params = new HashMap<String, String>()
            params.put("requestId", req.getOrderId())
            params.put("merchantCode", payment.getMerchantCode())
            params.put("totalBizType", "BIZ01100")
            params.put("totalPrice", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
                params.put("bankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()))
            }
            params.put("backurl", payment.getNotifyUrl() + merchant.getId())
            params.put("returnurl", payment.getNotifyUrl() + merchant.getId())
            params.put("noticeurl", payment.getNotifyUrl() + merchant.getId())
            params.put("description", "CZ")
            params.put("payType", "25")

            String toSign = req.getOrderId() + payment.getMerchantCode() + params.get("totalBizType") + params.get("totalPrice")
            +params.get("backurl") + params.get("returnurl") + params.get("noticeurl") + params.get("description")
            params.put("mersignature", SignatureUtil.hmacSign(toSign, payment.getPrivateKey()))

            params.put("productId", "1")
            params.put("productName", "CZ")
            params.put("fund", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            params.put("merAcct", payment.getMerchantCode())
            params.put("bizType", "BIZ01100")
            params.put("productNumber", "1")

            log.info("FuliScript_Prepare_Params:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
            String restr = okHttpUtil.post(payment.getPayUrl() + "/simplepay/pay_mobilePay", params, requestHeader)
            log.info("FuliScript_Prepare_resStr:{}", restr)

            if (StringUtils.isEmpty(restr)) {
                result.setErrorCode(1)
                result.setErrorMsg("创建订单失败，稍后重试")
                return
            }

            result.setMessage(restr)
        } catch (Exception e) {
            e.printStackTrace()
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        log.info("FuliScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("requestId")
        } else {
            orderId = json.getString("requestId")
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, payment, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        Map<String, String> params = new HashMap<String, String>()
        params.put("requestId", orderId + System.currentTimeMillis())
        params.put("originalRequestId", orderId)
        params.put("merchantCode", account.getMerchantCode())

        String toSign = params.get("requestId") + account.getMerchantCode() + params.get("originalRequestId")
        params.put("signature", SignatureUtil.hmacSign(toSign, account.getPrivateKey()))

        log.info("FuliScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/main/SearchOrderAction_merSingleQuery", params, requestHeader)
        log.info("FuliScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        if ("00000" == (json.getString("result")) && "2" == (json.getString("status"))) {
// 0：待处理（通过校验的初始状态） 1：处理中（支付执行中）2：成功（支付成功）3：失败（支付失败） 4：待确认
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("tradeSum").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("tradeId"))
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        Map<String, Object> params = new HashMap<String, Object>()
        params.put("requestId", req.getOrderId())
        params.put("merchantCode", merchantAccount.getMerchantCode())
        params.put("transferType", "1")
        params.put("sum", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("unionBankNum", "1")//联行号
        params.put("accountType", "1")//0：普通，1：快速
        params.put("branchBankName", "Shanghai")
        params.put("openBankName", "Shanghai")
        params.put("openBankProvince", "Shanghai")
        params.put("openBankCity", "Shanghai")
        params.put("accountName", req.getName())
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        params.put("bankAccount", req.getCardNo())
        params.put("reason", "withdraw")
        params.put("noticeUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("refundNoticeUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = req.getOrderId() + merchantAccount.getMerchantCode() + params.get("transferType") + params.get("sum")
        +params.get("accountType") + params.get("unionBankNum") + params.get("branchBankName") + params.get("openBankName")
        +params.get("openBankProvince") + params.get("openBankCity") + req.getName() + params.get("bankCode")
        +params.get("bankAccount") + params.get("reason") + params.get("noticeUrl") + params.get("refundNoticeUrl")
        params.put("signature", SignatureUtil.hmacSign(toSign, merchantAccount.getPrivateKey()))

        log.info("FuliScript_Transfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        Charset charset = Charset.forName("GBK")
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/main/singleTransfer_toTransfer", params, requestHeader, null, charset)
        log.info("FuliScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "00000" != (json.getString("result"))) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }


    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        log.info("FuliScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId
        if (json == null) {
            orderId = resMap.get("requestId")
        } else {
            orderId = json.getString("requestId")
        }
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        Map<String, String> params = new HashMap<String, String>()
        params.put("merchantCode", merchant.getMerchantCode())
        params.put("originalRequestId", orderId)
        params.put("requestId", (orderId + System.currentTimeMillis()))

        String toSign = params.get("requestId") + merchant.getMerchantCode() + params.get("originalRequestId")
        params.put("signature", SignatureUtil.hmacSign(toSign, merchant.getPrivateKey()))

        log.info("FuliScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/main/singleTransfer_singleTransferQuery", params, requestHeader)
        log.info("FuliScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("00000" == (json.getString("result"))) {
            GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId)
            if (glWithdraw == null) {
                return null
            }
            notify.setAmount(glWithdraw.getAmount().subtract(glWithdraw.getFee()))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //0：待系统自动复核（通过校验的初始状态）
            //1：已接收（付款复核通过）
            //2：成功（付款现成功）
            //3：失败（付款失败）
            //4：复核拒绝（付款复核拒绝）
            //9：已请求（渠道同步返回受理）
            //10：已退票（成功付款交易改为失败）
            if (json.getString("status") == ("2")) {//    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0)
            } else if (json.getString("status") == ("3") || json.getString("status") == ("10")) {
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
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.glWithdrawMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        Map<String, String> params = new HashMap<>()
        params.put("merchantCode", merchantAccount.getMerchantCode())
        params.put("requestId", System.currentTimeMillis() + "")

        String toSign = params.get("requestId") + merchantAccount.getMerchantCode()
        params.put("signature", SignatureUtil.hmacSign(toSign, merchantAccount.getPrivateKey()))

        log.info("FuliScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/main/MerchantAccountQueryAction_merchantAccountQuery", params, requestHeader)
        log.info("FuliScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "00000" == (json.getString("result"))) {
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
                .channelId(PaymentMerchantEnum.FULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FULI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
