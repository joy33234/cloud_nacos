package com.seektop.fund.payment.dongfanpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 东方内充 商户切换新系统
 *
 * @author walter
 */
public class DongfangScript {

    private static final Logger log = LoggerFactory.getLogger(DongfangScript.class)

    private OkHttpUtil okHttpUtil


    void pay(Object[] args) {

    }


    RechargeNotify notify(Object[] args) throws GlobalException {
        return null
    }


    RechargeNotify payQuery(Object[] args) throws GlobalException {
        return null
    }


    RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, String> params = new HashMap<>()
        params.put("version", "3.0")
        params.put("method", "Gt.online.pay")
        params.put("partner", merchantAccount.getMerchantCode())
        params.put("batchnumber", req.getOrderId())
        params.put("paymoney", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("cardNumber", req.getCardNo())

        String toSign = MD5.toAscii(params) + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        params.put("cardName", req.getName())
        params.put("bankName", req.getBankName())
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("remarks", "remarks")


        log.info("DongfangScript_Transfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/gateway/proxyPay", params, requestHeader)
        log.info("DongfangScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "1000" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
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
        log.info("DongfangScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("batchnumber")

        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "3.0")
        params.put("method", "Gt.online.payquery")
        params.put("partner", merchant.getMerchantCode())
        params.put("batchnumber", orderId)

        String toSign = MD5.toSign(params) + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("DongfangScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/gateway/proxyPay", params, requestHeader)
        log.info("DongfangScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if ("1000" == (json.getString("code"))) {
            JSONObject dataJSON = json.getJSONObject("data")
            notify.setAmount(dataJSON.getBigDecimal("paymoney"))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            if (dataJSON.getString("status") == ("1")) {
//三方商户订单状态： 0:处理中,1:成功到账,2:驳回申请,3:审核中4:失败    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == ("4") || dataJSON.getString("status") == ("2")) {
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
        Map<String, String> params = new LinkedHashMap<>()
        params.put("version", "3.0")
        params.put("method", "Gt.online.paybalance")
        params.put("partner", merchantAccount.getMerchantCode())

        String toSign = MD5.toSign(params) + merchantAccount.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        log.info("DongfangScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/gateway/proxyPay/balance", params, requestHeader)
        log.info("DongfangScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "1000" == (json.getString("code"))) {
            return json.getBigDecimal("data") == null ? BigDecimal.ZERO : json.getBigDecimal("data")
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
                .channelId(PaymentMerchantEnum.DONGFANG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.DONGFANG_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
