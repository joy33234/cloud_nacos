package com.seektop.fund.payment.fangzhoupay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.RedisKeyHelper
import com.seektop.constant.user.UserConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.withdraw.GlWithdrawBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class FangzhouScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(FangzhouScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private RedisService redisService

    private GlWithdrawBusiness withdrawBusiness

    private static final String url = "/bitpay-gateway/txn";

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        this.redisService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService


        WithdrawResult result = new WithdrawResult()

        BigDecimal usdtRate = null;
        if (UserConstant.UserType.PLAYER == req.getUserType()) {
            usdtRate  = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE, BigDecimal.class);
        } else {
            usdtRate  = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE_PROXY, BigDecimal.class);
        }
        BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN);
        BigDecimal usdtAmount = amount.divide(usdtRate, 4, RoundingMode.DOWN)

        BigDecimal vhkdRate = queryRate(merchantAccount)
        if (vhkdRate == null) {
            result.setReqData(merchantAccount.getChannelName())
            result.setOrderId(req.getOrderId())
            result.setResData(merchantAccount.getChannelName())
            result.setRate(usdtRate)
            result.setUsdtAmount(usdtAmount)
            result.setValid(false)
            result.setMessage("VHKD币汇率查询异常")
            return result
        }
        BigDecimal vhkdAmount = usdtAmount.multiply(vhkdRate).multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.DOWN);
        Date now = new Date()

        Map<String, String> params = new HashMap<>();
        params.put("txnType", "52")
        params.put("txnSubType", "50")
        params.put("secpVer", "icp3-1.1")
        params.put("secpMode", "perm")
        params.put("macKeyId", merchantAccount.getMerchantCode())
        params.put("merId", merchantAccount.getMerchantCode())
        params.put("userId", req.getOrderId())
        params.put("orderDate", DateUtils.format(now, DateUtils.YYYYMMDD))
        params.put("orderTime", DateUtils.format(now, DateUtils.HHMMSS))
        params.put("orderId", req.getOrderId())
        params.put("txnAmt", vhkdAmount.toString())
        params.put("currencyCode", "VHKD")
        params.put("unit", "0.01")
        params.put("exCurrencyCode", "USDT")
        params.put("exUnit", "1")
        params.put("address", req.getAddress())
        params.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(params) + "&k="+ merchantAccount.getPrivateKey()
        params.put("mac", MD5.md5(toSign))

        log.info("FangzhouUSDTScript_Transfer_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + url, params, requestHeader)
        log.info("FangzhouUSDTScript_Transfer_resStr = {}", resStr)

        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        result.setRate(usdtRate)
        result.setUsdtAmount(usdtAmount)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0000" != json.getString("respCode")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("respMsg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("respMsg"))
        result.setThirdOrderId(json.getString("txnId"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("FangzhouUSDTScript_Transfer_Notify_resMap = {}", JSON.toJSONString(resMap))
        String thirdOrderId = resMap.get("txnId")// 三方订单号
        String orderId = resMap.get("userId")// 三方订单号
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.withdrawBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlWithdrawBusiness) as GlWithdrawBusiness
        GlWithdraw glWithdraw = withdrawBusiness.findById(orderId)
        if (glWithdraw == null) {
            return null
        }

        Map<String, String> params = new HashMap<>();
        params.put("txnType", "00")
        params.put("txnSubType", "51")
        params.put("secpVer", "icp3-1.1")
        params.put("secpMode", "perm")
        params.put("macKeyId", merchant.getMerchantCode())
        params.put("merId", merchant.getMerchantCode())
        params.put("orderId", orderId)
        params.put("orderDate", DateUtils.format(glWithdraw.getCreateDate(), DateUtils.YYYYMMDD))
        params.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(params) + "&k="+ merchant.getPrivateKey()
        params.put("mac", MD5.md5(toSign))

        log.info("FangzhouUSDTScript_Transfer_Query_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + url, params, requestHeader)
        log.info("FangzhouUSDTScript_Transfer_Query_resStr = {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("respCode") != "0000") {
            return null
        }

        //交易状态 /01---处理中  10---交易成功  20---交易失败 30---其他状态(需联系管理人员)
        Integer status = json.getInteger("txnStatus")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setActualAmount(json.getBigDecimal("exAmount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark(json.getString("respMsg"))
        if (status == 10) {
            notify.setStatus(0)
        } else if (status == 20) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("txnId"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
          return BigDecimal.ZERO
    }


    public BigDecimal queryRate(GlWithdrawMerchantAccount merchant) {
        Map<String, String> params = new HashMap<>();
        params.put("txnType", "00")
        params.put("txnSubType", "91")
        params.put("secpVer", "icp3-1.1")
        params.put("secpMode", "perm")
        params.put("macKeyId", merchant.getMerchantCode())
        params.put("merId", merchant.getMerchantCode())
        params.put("txnAmt", "1")
        params.put("currencyCode", "USDT")
        params.put("unit", "1")
        params.put("exCurrencyCode", "VHKD")
        params.put("queryType", "5250")//交易类别(0181=充值/5250=代付)
        params.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(params) + "&k="+ merchant.getPrivateKey()
        params.put("mac", MD5.md5(toSign))
        log.info("FangzhouUSDTScript_queryRate_Params = {}", JSON.toJSONString(params))
        String restr = okHttpUtil.post(merchant.getPayUrl() + url, params, null)
        log.info("FangzhouUSDTScript_queryRate_restr = {}", restr)
        JSONObject json = JSON.parseObject(restr)
        if (json == null || json.getString("respCode") != "0000") {
            return null
        }
        return json.getBigDecimal("exAmount")
    }


    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, int channelId, String channelName) {
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