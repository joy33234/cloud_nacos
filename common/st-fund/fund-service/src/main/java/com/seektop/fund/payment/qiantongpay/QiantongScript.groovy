package com.seektop.fund.payment.qiantongpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 钱通支付
 *
 * @author walter
 */
public class QiantongScript {

    private static final Logger log = LoggerFactory.getLogger(QiantongScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private static Map<String, String> productMap = new HashMap<>()

    static {
        productMap.put("20000203", "支付宝H5/WAP T0支付")
        productMap.put("20000303", "支付宝T0扫码支付")
        productMap.put("10000203", "微信H5/WAP T0支付")
    }

    private static final String SERVEL_PAY = "/roncoo-pay-web-gateway/cnpPay/initPay"//支付地址
    private static final String SERVEL_ORDER_QUERY = "/roncoo-pay-web-gateway/query/singleOrder"//订单查询地址

    /**
     * 封装支付请求参数
     *
     * @param merchant
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
        String productType = ""
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            productType = "20000303"
        } else if (FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            productType = "20000201"
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            productType = "10000203"
        } else if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            productType = "10000201"
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            productType = "20000303"
        }
        prepareScan(merchant, payment, req, result, productType)
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String productType) throws GlobalException {
        try {
            Map<String, String> params = new HashMap<String, String>()
            params.put("payKey", account.getMerchantCode())
            params.put("productType", productType)
            params.put("outTradeNo", req.getOrderId())
            params.put("orderPrice", req.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "")
            params.put("orderTime", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
            params.put("productName", "CZ")
            params.put("orderIp", req.getIp())
            params.put("returnUrl", account.getNotifyUrl() + merchant.getId())
            params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())

            String sign = MD5.toAscii(params) + "&paySecret=" + account.getPrivateKey()
            params.put("sign", MD5.md5(sign).toUpperCase())

            log.info("QiantongScript_Prepare_resMap:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVEL_PAY, params, requestHeader)
            log.info("QiantongScript_Prepare_resStr{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            if (json.getString("resultCode") != ("0000")) {
                result.setErrorCode(1)
                result.setErrorMsg("订单创建失败，稍后重试")
                return
            }
            result.setRedirectUrl(json.getString("payMessage"))
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("QiantongScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("outTradeNo")
        if (null != orderId && "" != (orderId)) {
            return this.payQuery(okHttpUtil, payment, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<String, String>()
        params.put("payKey", account.getMerchantCode())
        params.put("outTradeNo", orderId)
        String sign = MD5.toAscii(params) + "&paySecret=" + account.getPrivateKey()
        params.put("sign", MD5.md5(sign).toUpperCase())
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        log.info("QiantongScript_query_params:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVEL_ORDER_QUERY, params, requestHeader)
        log.info("QiantongScript_query_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        // 订单状态判断标准:  【SUCCESS】：支付成功 【T0,T1】 【FINISH】交易完成 【T1订单对账完成时返回该状态值】
        //【FAILED】：支付失败 【WAITING_PAYMENT】：等待支付
        if (json.getString("resultCode") == ("0000") &&
                ("SUCCESS" == (json.getString("orderStatus")) || "FINISH" == (json.getString("orderStatus")))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("orderPrice"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("trxNo"))
            return pay
        }
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
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
 * 获取头部信息
 *
 * @param userId
 * @param userName
 * @param orderId
 * @return
 */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.QIANTONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.QIANTONG_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }


}
