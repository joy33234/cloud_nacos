package com.seektop.fund.payment.jiufuUSDT

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.RedisKeyHelper
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BlockInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class JiufuScript_USDT_recharge {

    private static final Logger log = LoggerFactory.getLogger(JiufuScript_USDT_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private RedisService redisService


    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.redisService = BaseScript.getResource(args[5], ResourceEnum.RedisService) as RedisService

        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY) {
            service = "10117"
        }
        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        } else {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {

        BigDecimal rate = redisService.get(RedisKeyHelper.PAYMENT_USDT_RATE, BigDecimal.class);
        BigDecimal usdtAmount = req.getAmount().divide(rate,4, RoundingMode.DOWN);

        Map<String, String> DataContentParams = new HashMap<String, String>()
        DataContentParams.put("partner", payment.getMerchantCode())
        DataContentParams.put("service", service)
        DataContentParams.put("tradeNo", req.getOrderId())
        DataContentParams.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        DataContentParams.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        DataContentParams.put("resultType", "json")


        String toSign = MD5.toAscii(DataContentParams) + "&" + payment.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())
        log.info("JiufuUSDTScript_Prepare_Params:{}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(),payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/unionOrder", DataContentParams, requestHeader)
        log.info("JiufuUSDTScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        String errorMsg = json.getString("msg")

        if (json.getString("isSuccess") != "T" || StringUtils.isEmpty(json.getString("url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(errorMsg)
            return
        }

        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setDigitalAmount(json.getBigDecimal("usdtAmount"))
        blockInfo.setProtocol(req.getProtocol())
        blockInfo.setBlockAddress(json.getString("address"))
        blockInfo.setRate(json.getBigDecimal("rate"))
        result.setBlockInfo(blockInfo)

        result.setBlockInfo(blockInfo);
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("JiufuUSDTScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("outTradeNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, payment, orderId, args[4])
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> DataContentParams = new HashMap<String, String>()
        DataContentParams.put("partner", account.getMerchantCode())
        DataContentParams.put("outTradeNo", orderId)
        DataContentParams.put("service", "10304")

        String toSign = MD5.toAscii(DataContentParams) + "&" + account.getPrivateKey()
        DataContentParams.put("sign", MD5.md5(toSign).toLowerCase())

        log.info("JiufuUSDTScript_Query_reqMap:{}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(),account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orderQuery", DataContentParams, requestHeader)
        log.info("JiufuUSDTScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("isSuccess") != "T") {
            return null
        }
        //0 处理理中 1 成功  2 失败
        if ("1" == json.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setPayDigitalAmount(json.getBigDecimal("usdtAmount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            log.info("pay:{}", JSON.toJSONString(pay))
            return pay
        }
        return null
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

    void cancel(Object[] args) throws GlobalException {

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

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
    }

}
