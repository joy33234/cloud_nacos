package com.seektop.fund.payment.antpay

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
import com.seektop.fund.payment.*
import org.apache.commons.lang.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class AntScript {

    private static final Logger log = LoggerFactory.getLogger(AntScript.class)

    private static final String SERVER_PAY_URL = "/index/index"//支付地址

    private static final String SERVER_QUERY_URL = "/index/index/get_status"//查询订单地址

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, String> params = new TreeMap<>()
        params.put("merchantid", account.getMerchantCode())
        params.put("orderid", req.getOrderId())
        params.put("money", req.getAmount() + "")
        params.put("client_ip", "0.0.0.0")
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            params.put("paytype", "alipayscan")//支付宝 动态金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            params.put("paytype", "alipayh5")//支付宝 固定金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            params.put("paytype", "wechat")//微信支付 动态金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            params.put("paytype", "weixinwap")//微信支付  固定金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            params.put("paytype", "tobank")
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            params.put("paytype", "alipaybank")
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY) {
            params.put("paytype", "alipaybank")
        }
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())
        params.put("return_url", account.getResultUrl() + merchant.getId())
        params.put("merchantKey", account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))
        params.put("format", "JSON")
        params.remove("merchantKey")
        log.info("AntScript_recharge_prepare_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.ANT_PAY.getCode().toString())
                .channelName(PaymentMerchantEnum.ANT_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String payUrl = account.getPayUrl() + SERVER_PAY_URL
        String resStr = okHttpUtil.post(payUrl, params, 10L, requestHeader)
        log.info("AntScript_recharge_prepare_resp = {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("status"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        JSONObject dataJson = json.getJSONObject("data")
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        result.setRedirectUrl(dataJson.getString("qr_code"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("AntScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderid")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantid", account.getMerchantCode())
        params.put("orderid", orderId)
        params.put("rndstr", RandomStringUtils.randomAlphabetic(10))
        params.put("merchantKey", account.getPrivateKey())
        params.put("sign", MD5.md5(MD5.toAscii(params)))
        params.remove("merchantKey")
        log.info("AntScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ANT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANT_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId.toUpperCase())
                .build()
        String queryUrl = account.getPayUrl() + SERVER_QUERY_URL
        String resStr = okHttpUtil.post(queryUrl, params, 10L, requestHeader)
        log.info("AntScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("1" != (json.getString("status"))) {
            return null
        }
        JSONObject dataJson = json.getJSONObject("data")

        // 请求成功 2-已付款，1-等待付款
        if ("2" == (dataJson.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            return pay
        }
        return null
    }

    void cancel(Object[] args) throws GlobalException {

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