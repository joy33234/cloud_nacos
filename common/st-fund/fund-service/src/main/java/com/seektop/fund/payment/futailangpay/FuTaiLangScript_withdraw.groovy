package com.seektop.fund.payment.futailangpay

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
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class FuTaiLangScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(FuTaiLangScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness



    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥

        String pay_memberid = merchantAccount.getMerchantCode()//商户号
        String pay_out_trade_no = req.getOrderId()
        String pay_money = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString()
        String pay_bankname = glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId())
        String pay_subbranch = "支行"
        String pay_accountname = req.getName()
        String pay_cardnumber = req.getCardNo()
        String pay_province = "上海市" //省
        String pay_city = "上海市"//城市
        String pay_notifyurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId()
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_out_trade_no", pay_out_trade_no)
        paramMap.put("pay_money", pay_money)
        paramMap.put("pay_bankname", pay_bankname)
        paramMap.put("pay_subbranch", pay_subbranch)
        paramMap.put("pay_accountname", pay_accountname)
        paramMap.put("pay_cardnumber", pay_cardnumber)
        paramMap.put("pay_province", pay_province)
        paramMap.put("pay_city", pay_city)
        paramMap.put("pay_notifyurl", pay_notifyurl)
        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        log.info("FuTaiLangScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", paramMap, 10L, requestHeader)
        log.info("FuTaiLangScript_Transfer_resStr: {}", resStr)

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
        log.info("FuTaiLangScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", merchant.getMerchantCode())
        paramMap.put("pay_out_trade_no", orderId)

        String signInfo = MD5.toAscii(paramMap)
        signInfo = signInfo + "&key=" + keyValue
        log.info("FuTaiLangScript_Transfer_Query_toSign: {}", signInfo)
        String pay_md5sign = MD5.md5(signInfo).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("FuTaiLangScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", paramMap, 10L, requestHeader)
        log.info("FuTaiLangScript_Transfer_Query_resStr: {}", resStr)

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
        //0:初始化    1:成功  2:失败  3:处理中 4:订单超时关闭
        if (refCode == 1) {
            notify.setStatus(0)
        } else if (refCode == 2) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("transaction_id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥
        String pay_noncestr = UUID.randomUUID().toString().replace("-", "")
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_noncestr", pay_noncestr)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign.toString()).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)

        log.info("FuTaiLangScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Pay_Balance_query.html", paramMap, 10L, requestHeader)
        log.info("FuTaiLangScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
    }

    void cancel(Object[] args) throws GlobalException {

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