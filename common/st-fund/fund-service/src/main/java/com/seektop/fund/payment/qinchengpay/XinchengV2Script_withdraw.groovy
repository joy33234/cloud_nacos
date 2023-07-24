package com.seektop.fund.payment.qinchengpay

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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc 鑫诚支付-V2
 * @date 2021-05-29
 * @auth joy
 */
public class XinchengV2Script_withdraw {


    private static final Logger log = LoggerFactory.getLogger(XinchengV2Script_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantCode", account.getMerchantCode())
        params.put("merchantOrderNo", req.getOrderId())
        params.put("currency", "CNY")
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("callbackUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("bankAccountName", req.getName())
        params.put("bankAccountNumber", req.getCardNo())
        params.put("bankSubbranch", "上海支行")
        params.put("city", "上海市")
        params.put("province", "上海市")
        params.put("signature", MD5.md5(MD5.toAscii(params) + "&" + account.getPrivateKey()))
        params.put("remark", "withdraw")

        log.info("XinchengScript_V2_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/v1/payout", JSON.toJSONString(params), requestHeader)
        log.info("XinchengScript_V2_doTransfer_resp:{}", resStr)

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
        if (json == null || json.getString("status") != "1" || StringUtils.isEmpty(json.getString("transactionNo"))) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("ok")
        result.setThirdOrderId(json.getString("transactionNo"))
        return result
    }


    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("XinchengScript_V2_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("merchantOrderNo")
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
        params.put("merchantCode", merchant.getMerchantCode())
        params.put("merchantOrderNo", orderId)
        params.put("signature", MD5.md5(MD5.toAscii(params) + "&" + merchant.getPrivateKey()))

        log.info("XinchengScript_V2_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/v1/payoutstatus", JSONObject.toJSONString(params), requestHeader)
        log.info("XinchengScript_V2_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "1") {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("transaction_id"))
        // // 支付状态:  1 : 成功, 2 : 失败, 3 : 待定
        if (json.getString("transactionStatus") == ("1")) {
            notify.setStatus(0)
            notify.setRsp("SUCCESS")
        } else if (json.getString("transactionStatus") == ("2")) {
            notify.setStatus(1)
            notify.setRsp("SUCCESS")
        } else {
            notify.setStatus(2)
        }
        return notify
    }
    //暂不支持
    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("merchantCode", merchantAccount.getMerchantCode())
        DataContentParams.put("currency", "CNY")
        DataContentParams.put("signature", MD5.md5(MD5.toAscii(DataContentParams) + "&" + merchantAccount.getPrivateKey()))

        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        log.info("XinchengScript_V2_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/v1/getbalance", JSON.toJSONString(DataContentParams), requestHeader)
        log.info("XinchengScript_V2_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || json.getString("status") != "1") {
            return BigDecimal.ZERO;
        }
        return json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance")
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