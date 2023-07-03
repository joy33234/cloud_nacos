package com.seektop.fund.payment.zhiyuanpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * @auth joy
 * @desc 质远支付
 */
class ZhiYuanScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(ZhiYuanScript_recharge.class)

    private OkHttpUtil okHttpUtil


    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "1"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("mchId", payment.getMerchantCode())
        DataContentParms.put("mchOrderNo", req.getOrderId())
        DataContentParms.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN) + "")
        DataContentParms.put("trueName", req.getFromCardUserName())
        DataContentParms.put("type", payType)
        DataContentParms.put("clientIp", req.getIp())
        DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())
        
        String toSign = MD5.toAscii(DataContentParms) + "&key=" + payment.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("ZhiYuanScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/api/obpay/create_oborder", DataContentParms, 30L, requestHeader)
        log.info("ZhiYuanScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null || json.getString("retCode") != "SUCCESS"
                || StringUtils.isEmpty(dataJSON.getString("accNo"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("retMsg"))
            return
        }
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("accName"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bankName"))
        bankInfo.setCardNo(dataJSON.getString("accNo"))
        result.setBankInfo(bankInfo)

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("ZhiYuanScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("mchOrderNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("mchId", account.getMerchantCode())
        DataContentParms.put("mchOrderNo", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

        log.info("ZhiYuanScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/obpay/getremitorder", DataContentParms, 30L, requestHeader)
        log.info("ZhiYuanScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("retCode")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")

        //0待汇款 1汇款成功
        if (dataJSON != null && "1" == dataJSON.getString("state")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
            return pay
        }
        return null
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
        return true
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
        return FundConstant.ShowType.DETAIL
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