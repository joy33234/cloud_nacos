package com.seektop.fund.payment.jubaofu

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.HtmlTemplateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 聚宝付支付接口
 *
 * @author walter
 */
public class JubaofuScript {

    private static final Logger log = LoggerFactory.getLogger(JubaofuScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String keyValue = payment.getPrivateKey()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("customerno", payment.getMerchantCode())
        paramMap.put("channeltype", "onlinebank")
        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "wechat_qrcode")
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "alipay_qrcode")
            if (req.getClientType() != 0) {
                paramMap.put("channeltype", "alipay_app")
            }
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            paramMap.put("channeltype", "yl_qrcode")
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "yl_nocard")
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "jd_qrcode")
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "onlinebank")
            paramMap.put("bankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchant.getChannelId()))
        }
        paramMap.put("customerbillno", req.getOrderId())
        paramMap.put("orderamount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("customerbilltime", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        paramMap.put("notifyurl", payment.getNotifyUrl() + merchant.getId())
        paramMap.put("returnurl", payment.getResultUrl() + merchant.getId())
        paramMap.put("ip", req.getIp())
        paramMap.put("devicetype", "web")
        paramMap.put("customeruser", req.getUsername())

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String sign = MD5.md5(toSign).toLowerCase()
        paramMap.put("sign", sign)

        log.info("JubaofuScript_Prepare_paramMap: {}", JSON.toJSONString(paramMap))
        String resultStr = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/api/gateway", paramMap)
        log.info("JubaofuScript_Prepare_result: {}", JSON.toJSONString(resultStr))
        result.setMessage(resultStr)
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("JubaofuScript_Notify_resMap: {}", JSON.toJSONString(resMap))
        String customerbillno = resMap.get("customerbillno")
        String preorderamount = resMap.get("preorderamount")
        if (StringUtils.isEmpty(customerbillno) || StringUtils.isEmpty(preorderamount)) {
            return null
        }
        return payQuery(okHttpUtil, payment, customerbillno + "-" + preorderamount, args[4])
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String[] order = orderId.split("-")
        Map<String, String> params = new HashMap<>()
        params.put("customerno", account.getMerchantCode())
        params.put("customerbillno", order[0])
        params.put("orderamount", order[1])
        log.info("JubaofuScript_Query_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JUBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JUBAOFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String result = okHttpUtil.post(account.getPayUrl() + "/api/query", params, requestHeader)
        log.info("JubaofuScript_Query_resStr: {}", result)
        if (StringUtils.isEmpty(result)) {
            return null
        }
        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        Boolean Result = json.getBoolean("Result")
        String PayStatus = json.getString("PayStatus")
        if (Result && "SUCCESS" == (PayStatus)) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("OrderAmount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(order[0])
            pay.setThirdOrderId(json.getString("OrderNo"))
            return pay
        }
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param payment
     * @param resMap
     * @return
     */
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
}
