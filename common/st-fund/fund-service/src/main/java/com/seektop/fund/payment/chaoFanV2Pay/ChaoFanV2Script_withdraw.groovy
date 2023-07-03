package com.seektop.fund.payment.chaoFanV2Pay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @desc 超凡v2支付
 * @date 2021-12-07
 * @auth Otto
 */
public class ChaoFanV2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(ChaoFanV2Script_withdraw.class)
    private static final String SERVER_WITHDRAW_URL = "/gateway"
    private static final String SERVER_QUERY_URL = "/gateway"
    private static final String SERVER_BALANCE_URL = "/gateway"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Date date = new Date();
        Map<String, String> params = new LinkedHashMap<>()
        params.put("service", "trade.payment")
        params.put("version", "1.0")
        params.put("merchantId", account.getMerchantCode())
        params.put("orderNo", req.getOrderId())
        params.put("tradeDate", DateUtils.format(date, DateUtils.YYYYMMDD))
        params.put("tradeTime", DateUtils.format(date, DateUtils.HHMMSS))
        params.put("amount", req.getAmount().multiply(100).setScale(0, RoundingMode.DOWN).toString())
        params.put("clientIp", req.getIp())
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("bankBranchName", req.getBankName())
        params.put("province", "江苏省")
        params.put("city", "南京市")
        params.put("benName", req.getName())
        params.put("benAcc", req.getCardNo())
        params.put("accType", "1")
        params.put("identityType", "01")
        params.put("identityNo", "448023198001044444") //身分證
        params.put("release", "1") //0 人工审单 1 走api
        params.put("resultType", "json")
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("key", account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))

        params.remove("key")

        log.info("ChaoFanScript_doTransfer_params:{} ,url:{}", JSON.toJSONString(params), account.getPayUrl())
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader)
        log.info("ChaoFanScript_doTransfer_resp:{} , orderId:{}", resStr, req.getOrderId())

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("网路超时:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if ("0001" != (json.getString("repCode"))) {
            result.setValid(false)
            result.setMessage(json.getString("repMsg"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("ChaoFanScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))

        JSONObject jsonObj = JSON.parseObject(resMap.get("reqBody"));

        Map<String, String> signMap = (Map<String, String>) jsonObj;
        String orderId = signMap.get("orderNo")
        String thirdSign = signMap.remove("sign")
        signMap.put("key", merchant.getPrivateKey())

        String sign = MD5.md5(MD5.toAscii(signMap))

        if (StringUtils.isNotEmpty(orderId) && sign == thirdSign) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId)

        }
        log.info("ChaoFanScript_doTransferNotify_Sign:回调错误 ,orderId:{}", orderId)
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Date date = new Date();
        Map<String, String> params = new LinkedHashMap<>()
        params.put("service", "trade.payment.query")
        params.put("version", "1.0")
        params.put("merchantId", merchant.getMerchantCode())
        params.put("orderNo", orderId)
        params.put("tradeDate", DateUtils.format(date, DateUtils.YYYYMMDD))
        params.put("tradeTime", DateUtils.format(date, DateUtils.HHMMSS))
        params.put("resultType", "json")
        params.put("key", merchant.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))

        params.remove("key")

        log.info("ChaoFanScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, requestHeader)
        log.info("ChaoFanScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0001" != json.getString("repCode")) {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)

        //0：未处理
        //1：处理中
        //2：已处理
        //4：汇出退回
        //5：订单不存在
        if (json.getString("resultCode") == "2") {

            //代付判断成功&失败条件
            //1. reusltCode=2
            //2. outCount=0 表示失败
            //3. 检查outAmount和outCount是否等于amount和totalCount，相等表示成功；不等表示失败
            if (json.getString("outCount") != "0" && json.getString("outAmount") == json.getString("amount")) {
                notify.setStatus(0)
                notify.setRsp("success")

            } else {
                notify.setStatus(1)
                notify.setRsp("success")

            }

        } else if (json.getString("status") == "4" || json.getString("status") == "5") {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount

        Date date = new Date();
        Map<String, String> params = new LinkedHashMap<>()
        params.put("service", "trade.fund.query")
        params.put("version", "1.0")
        params.put("merchantId", merchant.getMerchantCode())
        params.put("orderNo", DateUtils.format(date, DateUtils.YYYYMMDDHHMMSS))
        params.put("tradeDate", DateUtils.format(date, DateUtils.YYYYMMDD))
        params.put("tradeTime", DateUtils.format(date, DateUtils.HHMMSS))
        params.put("resultType", "json")
        params.put("key", merchant.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))

        params.remove("key")

        log.info("ChaoFanScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("ChaoFanScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0001" != (json.getString("repCode"))) {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("fundAvailableBalance")
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