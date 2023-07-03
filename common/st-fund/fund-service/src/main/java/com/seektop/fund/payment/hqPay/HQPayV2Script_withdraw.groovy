package com.seektop.fund.payment.hqPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.fasterxml.jackson.databind.ObjectMapper
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

import static com.seektop.fund.payment.groovy.BaseScript.getResource

/**
 * @desc HQ支付
 * @auth matt
 * @date 2022-04-06
 */
class HQPayV2Script_withdraw {
    private static final Logger log = LoggerFactory.getLogger(HQPayV2Script_withdraw.class)
    private OkHttpUtil okHttpUtil
    private ObjectMapper objectMapper = new ObjectMapper();
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness
    // 商户返回出款状态：0成功，1失败,2处理中
    // 對方狀態  PENDING，待处理。 LOCKED，在处理。AUDITED，已支付。REFUSED，已拒绝。

    int notifyState(JSONObject response){
       String tradeState = Optional.ofNullable(response)
               .map({ j -> j.getJSONObject("data") })
                .map({ j -> j.getString("status") })
                .orElse("PENDING");

        switch (tradeState) {
            case "AUDITED" :
                return 0
            case "REFUSED" :
                return 1
            case "PENDING" :
            case "LOCKED" :
            default:
                return 2
        }
    }

    boolean isSuccessResponse(JSONObject response) {
        return Optional.ofNullable(response)
                .map({ j -> j.getString("success") })
                .filter("true".&equals)
                .isPresent();
    }

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        log.info("HQPayScript_withdraw: {}", req)
        Map<String, String> params = new LinkedHashMap<>()
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("callback", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("merchant", merchantAccount.getMerchantCode())
        params.put("merchantBank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        params.put("merchantBankCardAccount", req.getCardNo());
        params.put("merchantBankCardBranch", req.getBankName());
        params.put("merchantBankCardCity", req.getBankName());
        params.put("merchantBankCardProvince", req.getBankName());
        params.put("merchantBankCardRealname", req.getName());
        params.put("requestReference",req.getOrderId())

        String code = MD5.toSign(params) + "&key=" + merchantAccount.getPrivateKey();
        String sign = MD5.md5(code).toUpperCase()

        params.put("sign",sign)
        log.info("HQPayScript_withdraw_Transfer_reqStr: {}", objectMapper.writeValueAsString(params))
        String resStr = okHttpUtil.post(
                merchantAccount.getPayUrl() + "/api/merchant/withdraw",
                params,
                getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        )
        log.info("HQPayScript_withdraw_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("网络请求超时，稍后重试.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)

        result.setValid(this.isSuccessResponse(json))
        result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HQPayScript_Notify_resMap:{}", JSON.toJSONString(resMap))

        String orderNo = resMap.get("requestReference")

        return withdrawQuery(okHttpUtil, merchant, orderNo)
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderNo = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchant", merchant.getMerchantCode())
        params.put("requestReference", orderNo)
        String sign = MD5.md5(MD5.toSign(params) + "&key=" + merchant.getPrivateKey()).toUpperCase();
        params.put("sign", sign)

        log.info("HQPay_Script_TransferQuery_reqMap:{}", params)
        String resStr = okHttpUtil.post(
                merchant.getPayUrl() + "/api/merchant/withdraw/query",
                params,
                getRequestHeard("", "", orderNo, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        )
        log.info("HQPay_Script_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)

        if (this.isSuccessResponse(json)) {
            int notifyState = this.notifyState(json);

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderNo)
            notify.setStatus(notifyState)
            notify.setRsp("success");
            log.info("HQPay_Script_TransferQuery_notifyState:{}", objectMapper.writeValueAsString(notify))
            return notify
        } else {
            return null;
        }
    }

    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchant", merchantAccount.getMerchantCode())

        String sign = MD5.md5(MD5.toSign(params)+ "&key=" + merchantAccount.getPrivateKey()).toUpperCase()

        params.put("sign", sign)
        String qUrl = merchantAccount.getPayUrl() + "/api/merchant/info"
        log.info("HQPayScript_withdraw_balanceQuery_reqStr: url:[{}] req:[{}]", qUrl, params)
        String resStr = okHttpUtil.post(
                qUrl,
                params,
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        );
        log.info("HQPayScript_withdraw_balanceQuery_resStr: {}", resStr)

        return Optional.ofNullable(resStr)
            .map(JSON.&parseObject)
            .filter(this.&isSuccessResponse)
            .map({ j -> j.getJSONObject("data") })
            .map({ j -> j.getBigDecimal("wallet") })
            .orElse(BigDecimal.ZERO)
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
