package com.seektop.fund.payment.fengNiaoPay

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
 * @desc 蜂鸟支付
 * @date 2021-12-04
 * @auth Otto
 */
public class FengNiaoScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(FengNiaoScript_withdraw.class)
    private static final String SERVER_WITHDRAW_URL = "/?c=Df&"
    private static final String SERVER_QUERY_URL = "/?c=Df&a=query&"
    private static final String SERVER_BALANCE_URL = "/?c=Df&a=balance&"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", account.getMerchantCode())
        params.put("ptype", "13")
        params.put("order_sn", req.getOrderId())
        params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("bankname", req.getBankName())
        params.put("accountname", req.getName())
        params.put("cardnumber", req.getCardNo())
        params.put("format", "json")
        params.put("notify_url",  account.getNotifyUrl() + account.getMerchantId())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + account.getPrivateKey()))

        log.info("FengNiaoScript_doTransfer_params:{} ,url:{}", JSON.toJSONString(params) ,account.getPayUrl() )
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_WITHDRAW_URL  , params, requestHeader)
        log.info("FengNiaoScript_doTransfer_resp:{} , orderId:{}", resStr ,req.getOrderId() )

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
        if (json == null ) {
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
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("FengNiaoScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))


        //出款失败的单，不主动回调 2021/12/05
        if (StringUtils.isNotEmpty(resMap.get("sh_order")) ) {
            String thirdSign = resMap.get("sign")

            Map<String, String> signMap = new LinkedHashMap<>();
            signMap.put("money",resMap.get("money"))
            signMap.put("pt_order",resMap.get("pt_order"))
            signMap.put("sh_order",resMap.get("sh_order"))
            signMap.put("real_money",resMap.get("real_money"))
            signMap.put("time",resMap.get("time"))
            signMap.put("status",resMap.get("status"))
            String sign = MD5.toAscii(signMap) + "&key=" + merchant.getPrivateKey();
            sign = MD5.md5(sign);

            if(sign == thirdSign){
                return this.withdrawQuery(okHttpUtil, merchant, resMap.get("sh_order"), args[3])
            }

        //系統定時撈取失敗訂單
        }else if(StringUtils.isNotEmpty(resMap.get("orderId")) ){
            return this.withdrawQuery(okHttpUtil, merchant, resMap.get("orderId"), args[3])

        }
        log.info("FengNiaoScript_doTransferNotify_Sign:回调错误 ,orderId:{}",resMap.get("sh_order") )

        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", merchant.getMerchantCode())
        params.put("out_order_sn", orderId)
        params.put("time", System.currentTimeSeconds().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + merchant.getPrivateKey()))

        log.info("FengNiaoScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL , params, requestHeader)
        log.info("FengNiaoScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        // 1待支付 2已提交 3已超时 4重新匹配 9已支付
        if (dataJSON.getString("status") == "9") {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (dataJSON.getString("status") == "3" || dataJSON.getString("status") == "4" )   {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mch_id", merchantAccount.getMerchantCode())
        params.put("time", System.currentTimeSeconds().toString())
        params.put("sign", MD5.md5(MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey()))

        log.info("FengNiaoScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params,  requestHeader)
        log.info("FengNiaoScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("code"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
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