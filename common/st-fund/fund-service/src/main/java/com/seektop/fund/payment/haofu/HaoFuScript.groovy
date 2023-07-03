package com.seektop.fund.payment.haofu

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
 * 豪富支付 ： 银联扫码、支付宝、微信
 *
 * @author walter
 */

public class HaoFuScript {

    private static final Logger log = LoggerFactory.getLogger(HaoFuScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            prepareTransfer(merchant, account, req, result, "10101", args[5])
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            prepareTransfer(merchant, account, req, result, "10103", args[5])
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            prepareTransfer(merchant, account, req, result, "10103", args[5]);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            prepareTransfer(merchant, account, req, result, "10108", args[5])
        }
    }


    private void prepareTransfer(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String service, Object[] args)
            throws GlobalException {
        String key = account.getPrivateKey()
        String partner = account.getMerchantCode()
        String amount = req.getAmount().toString()
        String trade_no = req.getOrderId()
        String notify_url = account.getNotifyUrl() + merchant.getId()


        Map<String, String> params = new HashMap<>();
        params.put("partner", partner);
        params.put("amount", amount);
        params.put("tradeNo", trade_no);
        params.put("notifyUrl", notify_url);
        params.put("service", service);
        params.put("resultType", "json");
        params.put("buyer", req.getFromCardUserName().trim());
        String toSign = MD5.toAscii(params) + "&" + key;
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("HaoFuScript_prepareTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/unionOrder", params, requestHeader)
        log.info("HaoFuScript_prepareTransfer_result:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if ("T" != json.getString("isSuccess")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            String errorMsg = StringUtils.isEmpty(json.getString("failMsg")) ? json.toJSONString() : json.getString("failMsg")
            result.setErrorMsg(errorMsg)
            return
        }
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(json.getString("url"))
//        String bankCardOwner = json.getString("bankCardOwner")
//        String cardNo = json.getString("cardNo")
//        String bankName = json.getString("bankName")
//
//        BankInfo bankInfo = new BankInfo();
//        this.glPaymentChannelBankBusiness = getResource(args[0], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
//        GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getBank(account.getChannelId(), bankName)
//        if (bank != null) {
//            bankInfo.setBankId(bank.getBankId())
//            bankInfo.setBankName(bank.getBankName())
//        } else {
//            bankInfo.setBankId(-1)
//            bankInfo.setBankName(bankName)
//        }
//        bankInfo.setBankBranchName(bankName)
//        bankInfo.setName(bankCardOwner)
//        bankInfo.setCardNo(cardNo)
//        result.setBankInfo(bankInfo)
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HaoFuScript_notify_resMap:{}", resMap)
        String orderId = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, account, orderId, args[3])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String key = account.getPrivateKey()
        String url = account.getPayUrl() + "/orderQuery"

        Map<String, String> params = new HashMap<>()
        params.put("partner", account.getMerchantCode());
        params.put("service", "10302");
        params.put("outTradeNo", orderId);
        String toSign = MD5.toAscii(params) + "&" + key
        String sign = MD5.md5(toSign).toLowerCase()
        params.put("sign", sign)
        log.info("HaoFuScript_payQuery_Params_:{}", params)
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(url, params, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("HaoFuScript_payQuery_response_:{}", json)
        //0:处理中   1:成功      2:失败
        if (json == null || "T" != (json.getString("isSuccess"))
                || json.getString("status") != ("1")) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount"))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId("")
        return pay
    }


    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String key = merchantAccount.getPrivateKey()

        Map<String, String> params = new HashMap<>();
        params.put("partner", merchantAccount.getMerchantCode());
        params.put("service", "10201");
        params.put("tradeNo", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("bankCardNo", req.getCardNo());
        params.put("bankCardholder", req.getName());
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("subsidiaryBank", "上海市");//分行
        params.put("subbranch", "上海市");//支行
        params.put("province", "上海市");//省份
        params.put("city", "上海市");//城市

        String toSign = MD5.toAscii(params) + "&" + key

        String sign = MD5.md5(toSign).toLowerCase()
        params.put("sign", sign)

        log.info("HaoFuScript_transfer_param:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/agentPay", params, requestHeader)
        log.info("HaoFuScript_transfer_response:{}", JSON.toJSONString(resStr))
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "T" != (json.getString("isSuccess"))) {
            result.setValid(false)
            result.setMessage(json == null ? "第三方接口错误" : json.getString("msg"))
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
        log.info("HaoFuScript_withdrawNotify_resMap:{}", resMap)
        String orderId = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        String key = merchant.getPrivateKey()
        String url = merchant.getPayUrl() + "/orderQuery"

        Map<String, String> params = new HashMap<>()
        params.put("partner", merchant.getMerchantCode());
        params.put("outTradeNo", orderId);
        params.put("service", "10301");
        String toSign = MD5.toAscii(params) + "&" + key
        String sign = MD5.md5(toSign).toLowerCase()
        params.put("sign", sign)
        log.info("HaoFuScript_withdrawQuery_Params:{}", params)
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(url, params, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("HaoFuScript_withdrawQuery_Response : {}", json)
        if (json == null || "T" != (json.getString("isSuccess"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("msg"))
        //0:处理中  1:成功   2:失败   3:处理中   4:处理中
        if (json.getString("status") == ("1")) {
            notify.setStatus(0)
        } else if (json.getString("status") == ("2")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }

        return notify
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String key = merchantAccount.getPrivateKey()
        String url = merchantAccount.getPayUrl() + "/balanceQuery"
        Map<String, String> params = new HashMap<>()
        params.put("partner", merchantAccount.getMerchantCode())
        params.put("service", "10401")
        String toSign = MD5.toAscii(params) + "&" + key
        String sign = MD5.md5(toSign).toLowerCase()
        params.put("sign", sign)
        log.info("HaoFuScript_balanceQuery_Params:{}", params)
        GlRequestHeader requestHeader = getRequestHeard(null, null, null, GlActionEnum.WITHDRAW_QUERY.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(url, params, requestHeader)
        JSONObject json = JSON.parseObject(resStr)
        log.info("HaoFuScript_balanceQuery_Response:{}", json)
        if (json == null || "T" != (json.getString("isSuccess"))) {
            return BigDecimal.ZERO
        }
        return json.getBigDecimal("balance")
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
//        Integer paymentId = args[1] as Integer
//        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
//                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
//                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
//            return FundConstant.ShowType.DETAIL
//        }
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