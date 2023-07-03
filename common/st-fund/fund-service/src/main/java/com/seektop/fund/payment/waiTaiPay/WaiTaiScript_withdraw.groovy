package com.seektop.fund.payment.waiTaiPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 万泰支付
 * @auth otto
 * @date 2021-10-26
 */

class WaiTaiScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(WaiTaiScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/Payment_Dfpay_add.html"
    private static final String SERVER_QUERY_URL = "/Payment_Dfpay_query.html"
    private static final String SERVER_BALANCE_URL = "/Payment_Dfpay_balance.html"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchid", merchantAccount.getMerchantCode())
        paramMap.put("out_trade_no", req.getOrderId())
        paramMap.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("bankname", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        paramMap.put("subbranch", "支行")
        paramMap.put("accountname", req.getName())
        paramMap.put("cardnumber", req.getCardNo())
        paramMap.put("province", "河南省")
        paramMap.put("city", "洛阳")
        paramMap.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("pay_md5sign", MD5.md5(toSign).toUpperCase())

        log.info("WaiTaiScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, paramMap,  requestHeader)
        log.info("WaiTaiScript_Transfer_resStr: {} , orderId:{}" , resStr , req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "success" != json.getString("status")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("WaiTaiScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))

        Map<String, String> signMap = new HashMap();
        signMap.put("status",resMap.get("status"))
        signMap.put("msg",resMap.get("msg"))
        signMap.put("mchid",resMap.get("mchid"))
        signMap.put("out_trade_no",resMap.get("out_trade_no"))
        signMap.put("amount",resMap.get("amount"))
        signMap.put("transaction_id",resMap.get("transaction_id"))
        signMap.put("refCode",resMap.get("refCode"))
        signMap.put("refMsg",resMap.get("refMsg"))

        if ( resMap.containsKey("success_time") ){  //出款成功时返回
            signMap.put("success_time",resMap.get("success_time"))
        }
        String thirdSign = resMap.get("pay_md5sign"); ;

        String toSign = MD5.toAscii(signMap) + "&key=" + merchant.getPrivateKey();
        toSign = MD5.md5(toSign).toUpperCase();

        if (StringUtils.isNotEmpty(resMap.get("out_trade_no")) && toSign == thirdSign) {
            return this.withdrawQuery(okHttpUtil, merchant, resMap.get("out_trade_no"))
        }

        log.info("WaiTaiScript_notify_Sign: 回调资料错误或验签失败，单号：{}", resMap.get("out_trade_no"))
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchid", merchant.getMerchantCode())
        paramMap.put("out_trade_no", orderId)

        String signInfo = MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()
        paramMap.put("pay_md5sign", MD5.md5(signInfo).toUpperCase())

        log.info("WaiTaiScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() +SERVER_QUERY_URL, paramMap, requestHeader)
        log.info("WaiTaiScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("refMsg"))

        //1	成功   2：失败   3：处理中  4：待处理   5：审核驳回  6	待审核   7:交易不存在   8:未知状态
        Integer refCode = json.getInteger("refCode")
        if (refCode == 1) {
            notify.setStatus(0)
            notify.setRsp("OK")

        } else if (refCode == 2 || refCode == 7 || refCode == 5) {
            notify.setStatus(1)
            notify.setRsp("OK")

        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("transaction_id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchid", merchantAccount.getMerchantCode())

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("pay_md5sign", MD5.md5(toSign.toString()).toUpperCase())

        log.info("WaiTaiScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL , paramMap, 30L, requestHeader)
        log.info("WaiTaiScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }


    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }



}