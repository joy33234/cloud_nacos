package com.seektop.fund.payment.tongfu

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

public class TongFuScript {

    private static final Logger log = LoggerFactory.getLogger(TongFuScript.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("payAmount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("commercialOrderNo", req.getOrderId())
        paramMap.put("callBackUrl", account.getNotifyUrl() + merchant.getId())
        paramMap.put("notifyUrl", account.getResultUrl() + merchant.getId())

        String payURL = ""
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            paramMap.put("payType", "2")
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyGetQrCode"
            if (ProjectConstant.ClientType.PC != req.getClientType()) {//PC
                payURL = account.getPayUrl() + "/api/guest/pay/payApplyGetQrCodeH5"
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            paramMap.put("payType", "3")
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyYsf"
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                paramMap.put("isMobile", "2")
            } else if (req.getClientType() == ProjectConstant.ClientType.APP) {
                paramMap.put("isMobile", "1")
            }
            payURL = account.getPayUrl() + "/api/guest/tianbao/wxPay"
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("payType", "2")
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyApi"
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyToBank"
        }
        String json = JsonUtil.toJson(paramMap)
        //MD5得到sign
        String sign = EncryptUtil.md5(json)
        //aes 加密得到 parameter
        String parameter = EncryptUtil.aes(json, account.getPrivateKey())

        Map<String, String> param = new HashMap<>()
        param.put("platformno", account.getMerchantCode())
        param.put("parameter", parameter)
        param.put("sign", sign)

        log.info("TongFuScript_prepare_param:{}", param)
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.DEPOSIT.getCode())
                .channelId(PaymentMerchantEnum.TONGFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TONGFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()

        String resStr = okHttpUtil.post(payURL, param, requestHeader)
        log.info("TongFuScript_prepare_resStr:{}", resStr)
        JSONObject jsonObj = JSON.parseObject(resStr)

        if (null == jsonObj || jsonObj.getString("result") != ("success")) {
            result.setErrorCode(1)
            result.setErrorMsg("订单创建失败，稍后重试")
            return
        }

        if (StringUtils.isNotEmpty(jsonObj.getString("payUrl")) && jsonObj.getString("payUrl") != ("null")) {
            result.setRedirectUrl(jsonObj.getString("payUrl"))
        }

        if (StringUtils.isNotEmpty(jsonObj.getString("localtionView")) && jsonObj.getString("payUrl") != ("localtionView")) {
            result.setRedirectUrl(jsonObj.getString("localtionView"))
        }
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("TongFuScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String parameter = resMap.get("parameter")
        if (StringUtils.isEmpty(parameter)) {
            return null
        }
        Map<String, String> paramMap = JsonUtil.toBean(DecryptUtil.aes(parameter, account.getPrivateKey()), Map.class)
        if (ObjectUtils.isEmpty(paramMap)) {
            return null
        }
        if ("faild" == (paramMap.get("result"))) {
            return null
        }
        String orderId = paramMap.get("commercialOrderNo")
        return payQuery(okHttpUtil, account, orderId)
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("commercialOrderNo", orderId)
        paramMap.put("type", "1")

        String json = JsonUtil.toJson(paramMap)
        //MD5得到sign
        String md5Sign = EncryptUtil.md5(json)
        //aes 加密得到 parameter
        Optional<String> aesSign = Optional.ofNullable(EncryptUtil.aes(json, account.getPrivateKey()))

        Map<String, String> param = new HashMap<>()
        param.put("platformno", account.getMerchantCode())
        param.put("parameter", aesSign.orElse(StringUtils.EMPTY))
        param.put("sign", md5Sign)

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.TONGFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TONGFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        log.info("TongFuScript_query_param:{}", JSON.toJSONString(param))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/guest/pay/commercialInfo", param, requestHeader)

        log.info("TongFuScript_query_resStr:{}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject jsonObj = JSON.parseObject(resStr)
        if (jsonObj == null || "faild" == (jsonObj.getString("result")) || "支付成功" != (jsonObj.getString("status"))) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()

        pay.setAmount(jsonObj.getBigDecimal("orderAmount"))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(jsonObj.getString("orderNo"))
        return pay
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
        return false
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
        return FundConstant.ShowType.NORMAL
    }
}
