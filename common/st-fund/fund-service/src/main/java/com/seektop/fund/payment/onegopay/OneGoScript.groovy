package com.seektop.fund.payment.onegopay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.cfpay.MerchantJSON
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * OneGoScript  接口
 *
 * @author walter
 */
public class OneGoScript {

    private static final Logger log = LoggerFactory.getLogger(OneGoScript.class)

    private OkHttpUtil okHttpUtil


    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, Object> params = new HashMap<String, Object>()
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("out_trade_no", req.getOrderId())
        params.put("notify_url", merchantaccount.getNotifyUrl() + merchant.getId())
        params.put("paid_name", req.getFromCardUserName())

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantaccount.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")

        log.info("OneGoScript_Prepare_resMap = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchantaccount.getChannelId(), merchantaccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/transaction", JSON.toJSONString(params), headParams, requestHeader)

        JSONObject json = JSON.parseObject(resStr)
        log.info("OneGoScript_Prepare_resStr = {}", json)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (StringUtils.isEmpty(json.getString("uri"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(json.getString("uri"))
        result.setThirdOrderId(json.getString("trade_no"));
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        Map<String, String> resMap = args[3] as Map<String, String>
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        log.info("OneGoScript_notify_resp = {}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String thirdOrderId = json.getString("trade_no")
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        log.info("OneGoScript_query_params_orderId = {}", orderId)

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + account.getPrivateKey())
        headParams.put("Accept", "application/json")

        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.get(account.getPayUrl() + "/api/transaction/" + orderId, null, requestHeader, headParams)

        JSONObject json = JSON.parseObject(resStr)
        log.info("OneGoScript_query_resStr = {}", json)
        if (json == null) {
            return null
        }
        // 订单状态判断标准:  success => 成功  progress => 进行中 timeout => 逾时
        if (json.getString("status") == ("success")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("trade_no"))
            pay.setRsp("ok")
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
        return null
    }


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, Object> params = new LinkedHashMap<String, Object>()
        params.put("application_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank", req.getBankName())
        params.put("bank_branch", "上海市")
        params.put("card_sn", req.getCardNo())
        params.put("out_trade_no", req.getOrderId())
        params.put("owner", req.getName())

        params.put("sign", getSign(MerchantJSON.encode(params), merchantAccount.getPrivateKey()))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        log.info("OneGoScript_doTransfer_resMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", params, requestHeader, headParams)
        JSONObject json = JSON.parseObject(resStr)
        log.info("OneGoScript_doTransfer_resStr:{}", json)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        if (json == null || "200" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("message"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("OneGoScript_transfer_notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchant.getPrivateKey())
        headParams.put("Accept", "application/json")
        headParams.put("content-type", "application/json")


        log.info("OneGoScript_TransferQuery_orderId_{}", orderId)
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/withdraw/" + orderId, null, requestHeader, headParams)

        JSONObject json = JSON.parseObject(resStr)
        log.info("OneGoScript_TransferQuery_resStr:{}", json)

        if (json == null || json.getString("code") != ("200")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("result")
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("application_amount"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataJSON.getString("out_trade_no"))
            notify.setThirdOrderId("")
            if (dataJSON.getString("status") == ("success")) {
                //订单状态progress=>申请中 withdrawing => 下发中 success => 成功 failed => 退回    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0)
                notify.setRsp("ok")
            } else if (dataJSON.getString("status") == ("failed")) {
                notify.setStatus(1)
                notify.setRsp("ok")
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey())
        headParams.put("Accept", "application/json")

        GlRequestHeader requestHeader = getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/withdraw/balance", null, requestHeader, headParams)

        JSONObject json = JSON.parseObject(resStr)
        log.info("TgaopayScript_queryBalance_resStr:{}", json)
        if (json != null && StringUtils.isNotEmpty(json.getString("balance"))) {
            String balanceStr = json.getString("balance").replaceAll(",", "")
            return new BigDecimal(balanceStr)
        }
        return BigDecimal.ZERO
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException* @throws UnsupportedEncodingException
     */
    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder()
        Mac sha256 = Mac.getInstance("HmacSHA256")
        sha256.init(new SecretKeySpec(accessToken.getBytes("UTF8"), "HmacSHA256"))

        return encoder.encodeToString(sha256.doFinal(value.getBytes("UTF8")))
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
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
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
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