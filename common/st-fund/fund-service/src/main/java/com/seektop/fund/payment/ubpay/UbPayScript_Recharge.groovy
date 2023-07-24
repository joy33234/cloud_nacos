package com.seektop.fund.payment.ubpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BlockInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * UBPAY
 * USDT商户
 *
 * @author darren
 */
public class UbPayScript_Recharge {

    private static final Logger log = LoggerFactory.getLogger(UbPayScript_Recharge.class)

    private OkHttpUtil okHttpUtil

    private static final BigDecimal rechargeRate = new BigDecimal(6.6)


    /**
     * 封装支付请求参数
     *
     * @param pay_bankcode @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO prepareDO = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String keyValue = payment.getPrivateKey()

        Date expireTime = DateUtils.addMin(60, prepareDO.getCreateDate())

        BigDecimal rate = getRate(payment);
        BigDecimal usdtAmount = prepareDO.getAmount().divide(rate, 3, RoundingMode.DOWN)

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("blockchain_rate", rate.toString())
        paramMap.put("out_trade_no", prepareDO.getOrderId())
        paramMap.put("amount", usdtAmount)
        paramMap.put("callback_url", payment.getNotifyUrl() + merchant.getId())

        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + keyValue)

        log.info("UbPayScript_Recharge_Prepare_paramMap = {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = this.getRequestHeard(prepareDO.getUserId().toString(), prepareDO.getUsername(), prepareDO.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String payUrl = payment.getPayUrl() + "/api/transaction"
        String respStr = okHttpUtil.postJSON(payUrl, JSON.toJSONString(paramMap), head , requestHeader)

        JSONObject respJson = JSON.parseObject(respStr)
        log.info("UbPayScript_Recharge_Prepare_respStr = {}", JSON.toJSONString(respJson))
        if (null == respJson) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = respJson.getJSONObject("data")
        if (!respJson.getBoolean("success") || dataJSON == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("请求失败，请联系三方商户")
            return
        }

        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setDigitalAmount(usdtAmount)
        blockInfo.setProtocol(prepareDO.getProtocol())
        blockInfo.setBlockAddress(dataJSON.getString("address"))
        blockInfo.setRate(rate)
        blockInfo.setExpiredDate(expireTime)
        result.setThirdOrderId(dataJSON.getString("trade_no"))

        result.setBlockInfo(blockInfo)

    }


    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("UbPayScript_Recharge_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("out_trade_no")
        String thirdOrderId = json.getString("trade_no")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return payQuery(okHttpUtil, payment, orderId, thirdOrderId, args[4])
        }
        return null
    }

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + payment.getPrivateKey())
        head.put("Accept", "application/json")
        head.put("Content-Type", "application/json")

        GlRequestHeader requestHeader = this.getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String queryUrl = payment.getPayUrl() + "/api/transaction/" + orderId
        String respStr = okHttpUtil.get(queryUrl, null, requestHeader,head)
        log.info("UbPayScript_Recharge_Query_result = {}", respStr)

        JSONObject respJson = JSON.parseObject(respStr)
        if (null == respJson || !respJson.getBoolean("success")) {
            return null
        }

        JSONObject dataJSON = respJson.getJSONObject("data")
        //new => 新订单 processing => 处理中  verify => 待確認  reject => 拒绝  completed => 成功  failed => 失败  refund => 冲回
        if (dataJSON != null && dataJSON.getString("state") == "completed") {
            BigDecimal rate = getRate(payment);
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").multiply(rate).setScale(4, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
            pay.setRsp("ok")
            return pay
        }
        return null
    }

    private BigDecimal getRate(GlPaymentMerchantaccount payment) {
        Map<String, String> head = new HashMap<>()
        head.put("Authorization","Bearer " + payment.getPrivateKey())
        head.put("Accept","application/json")
        head.put("Content-Type","application/json")

        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String respStr = okHttpUtil.get(payment.getPayUrl() + "/api/blockchain/rate",  null, requestHeader,head)
        log.info("UbPayScript_query_rate = {}", respStr)

        JSONObject json = JSONObject.parseObject(respStr)
        if (json == null || !json.getBoolean("success") || json.getJSONArray("data") == null) {
            return rechargeRate
        } else {
            JSONArray array = json.getJSONArray("data")
            for (JSONObject rateJSON : array){
                if (rateJSON.getString("platform") == "huobi") {
                    return rateJSON.getBigDecimal("rate")
                }
            }
        }
    }


    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return true
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }


    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        return FundConstant.ShowType.DIGITAL
    }

    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
