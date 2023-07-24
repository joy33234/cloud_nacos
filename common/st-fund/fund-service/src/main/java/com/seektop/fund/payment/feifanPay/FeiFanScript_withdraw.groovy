package com.seektop.fund.payment.feifanPay

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

class FeiFanScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(FeiFanScript_withdraw.class)

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
        paramMap.put("bankname", req.getBankName())
        paramMap.put("subbranch", "支行")
        paramMap.put("accountname", req.getName())
        paramMap.put("cardnumber", req.getCardNo())
        paramMap.put("province", "上海市")
        paramMap.put("city", "上海市")
        paramMap.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("sign", pay_md5sign)
        log.info("FeiFanScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", paramMap, 30L, requestHeader)
        log.info("FeiFanScript_Transfer_resStr: {}", resStr)

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
        if (json == null || "success" != json.getString("status") || StringUtils.isEmpty(json.getString("transaction_id"))) {
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
        log.info("FeiFanScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("out_trade_no")// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchid", merchant.getMerchantCode())
        paramMap.put("out_trade_no", orderId)

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("FeiFanScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", paramMap, 30L, requestHeader)
        log.info("FeiFanScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || json.getString("status") != "success") {
            return null
        }

        Integer refCode = json.getInteger("refCode")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("refMsg"))
        // 1:成功  2:失败  3:处理中 4:待处理  5:审核驳回  6:待审核  7：交易不存在  8：未知状态
        if (refCode == 1) {
            notify.setStatus(0)
            notify.setRsp("OK")
        } else if (refCode == 2 || refCode == 5) {
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
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchid", pay_memberid)

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("pay_md5sign", MD5.md5(toSign).toUpperCase())
        log.info("FeiFanScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_balance.html", paramMap, 30L, requestHeader)
        log.info("FeiFanScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
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