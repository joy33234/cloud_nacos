package com.seektop.fund.payment.yinDaPay

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
 * @desc 银达代付
 * @date 2021-11-17
 * @auth Otto
 */
class YinDaScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(YinDaScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/server/withdrawal/appwithdrawal"
    private static final String SERVER_QUERY_URL = "/server/api/withdrawQuery"
    private static final String SERVER_BALANCE_URL = "/server/api/useramount"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> params = new LinkedHashMap<>()

        params.put("account_id", merchantAccount.getMerchantCode())
        params.put("out_trade_no", req.getOrderId())
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bank_name", req.getBankName())
        params.put("bank_user", req.getName())
        params.put("bank_id", req.getCardNo())
        params.put("callback_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("withdraw_type", "1")

        String toSign = MD5.md5(  MD5.md5(merchantAccount.getMerchantCode() + req.getOrderId() + req.getCardNo() ) + merchantAccount.getPrivateKey() ) ;
        params.put("sign", toSign)

        log.info("YinDaScript_Transfer_params: {}  postUrl:{}", JSON.toJSONString(params) ,merchantAccount.getPayUrl() )
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("YinDaScript_Transfer_resStr: {}  , orderid :{}", resStr, req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        // code: 200 下单成功 ，其余失败
        if (200 != json.getInteger("code")) {
            result.setValid(false)
            result.setMessage(json.getString("msg") == null ? "三方下单失败" : json.getString("msg"))
            return result
        }

        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("YinDaScript_WithdrawNotify_resMap:{}", resMap)

        String orderId = resMap.get("flow_no")
        String md5Sign = MD5.md5( orderId +resMap.get("call_time") +merchant.getPrivateKey());

        if (StringUtils.isNotEmpty(orderId) && resMap.get("sign") == md5Sign) {
            return withdrawQuery(okHttpUtil, merchant, orderId)

        }
        log.info("YinDaScript_WithdrawNotify_Sign: 回调资料错误或验签失败 单号：{}", orderId)
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2] as String

            Map<String, String> params = new HashMap<String, String>()
            params.put("ddh", orderId)

            log.info("YinDaScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())

            String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
            log.info("YinDaScript_TransferQuery_resStr:{} ", resStr)

            if (StringUtils.isEmpty(resStr)) {
                return null
            }
        JSONObject json = JSON.parseObject(resStr)
        WithdrawNotify notify = new WithdrawNotify()

            // code 返回码： 200=成功，其他失败
            if (200 == json.getInteger("code")) {
                notify.setMerchantCode(merchant.getMerchantCode())
                notify.setMerchantId(merchant.getMerchantId())
                notify.setMerchantName(merchant.getChannelName())
                notify.setOrderId(orderId)
                notify.setThirdOrderId("")

                //1打款中 2提现已到账 3提现已驳回
                String payStatus = json.getString("msg")
                if (payStatus == "2") {
                    notify.setStatus(0)
                    notify.setRsp("success")

                } else if (payStatus == "3") {
                    notify.setStatus(1)
                    notify.setRsp("success")

                } else {
                    notify.setStatus(2)

                }
            }
            return notify
        }

        BigDecimal balanceQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
            this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

            Map<String, String> params = new HashMap<>()
            params.put("id", merchantAccount.getMerchantCode())

            log.info("YinDaScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
            log.info("YinDaScript_QueryBalance_resStr: {}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            //"code":200 成功
            if (json == null || json.getInteger("code") != 200) {
                return BigDecimal.ZERO
            }

            BigDecimal balance = json.getBigDecimal("msg")
            return balance == null ? BigDecimal.ZERO : balance
        }

        private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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