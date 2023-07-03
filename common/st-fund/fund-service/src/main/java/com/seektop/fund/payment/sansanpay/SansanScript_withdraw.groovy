package com.seektop.fund.payment.sansanpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc  gi-global支付
 * @date 2021-03-30
 * @auth joy
 */
public class SansanScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(SansanScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("app_id", account.getMerchantCode())
        params.put("format", "JSON")
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("sign", MD5.md5(account.getPrivateKey() + account.getMerchantCode() + stamptime));
        params.put("stamptime", stamptime)
        params.put("version", "1.0")
        params.put("notify_url", account.getNotifyUrl() + account.getMerchantId())

        //业务参数
        params.put("orderid", req.getOrderId())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("payee", req.getName())
        params.put("card", req.getCardNo())
        params.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        params.put("open", "上海支行")
        params.put("biz_content", JSON.toJSONString(params))

        log.info("SansanScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/addons/payment/order/behalf", JSON.toJSONString(params), requestHeader)
        log.info("SansanScript_doTransfer_resp:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("1" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("SansanScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody")).getJSONObject("biz_content")
        String orderId = json.getString("orderid")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("app_id", merchant.getMerchantCode())
        params.put("format", "JSON")
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("sign", MD5.md5(merchant.getPrivateKey() + merchant.getMerchantCode() + stamptime));
        params.put("stamptime", stamptime)
        params.put("version", "1.0")
        params.put("notify_url", merchant.getNotifyUrl() + merchant.getMerchantId())

        //业务参数
        params.put("orderid", orderId)
        params.put("biz_content", JSON.toJSONString(params))

        log.info("SansanScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/addons/payment/order/get_behalf_order", JSONObject.toJSONString(params), requestHeader)
        log.info("SansanScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data").getJSONArray("list").get(0)

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId("")
        //订单状态 状态:0=未处理,1=已处理,2=已撤销
        if (dataJSON.getString("status") == ("1")) {
            notify.setStatus(0)
        } else if (dataJSON.getString("status") == ("2")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        log.info(JSON.toJSONString(notify))
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("app_id", merchantAccount.getMerchantCode())
        params.put("format", "JSON")
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("sign", MD5.md5(merchantAccount.getPrivateKey() + merchantAccount.getMerchantCode() + stamptime));
        params.put("stamptime", stamptime)
        params.put("version", "1.0")
        params.put("notify_url", "")
        params.put("biz_content", JSON.toJSONString(params))

        log.info("SansanScript_queryBalance_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/addons/payment/order/api_get_balance", JSONObject.toJSONString(params), requestHeader)
        log.info("SansanScript_queryBalance_resp:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if ("1" == (json.getString("code")) && ObjectUtils.isNotEmpty(json.getJSONObject("data"))) {
            return json.getJSONObject("data").getBigDecimal("balance")
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
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}