package com.seektop.fund.payment.juheweixinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.mapper.GlRechargeMapper
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlRecharge
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 聚合微信
 *
 */
public class JuheWeixinScript {

    private static final Logger log = LoggerFactory.getLogger(JuheWeixinScript.class)

    private OkHttpUtil okHttpUtil

    private GlRechargeMapper rechargeMapper


    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.rechargeMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlRechargeMapper) as GlRechargeMapper
        String requestNo = System.currentTimeMillis() + ""//交易流水号
        String productId = "SY01"
        String transId = "01"
        String transAmt = (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString()

        StringBuilder signStr = new StringBuilder()
        signStr.append(requestNo)
        signStr.append(productId)
        signStr.append(transId)
        signStr.append(merchantaccount.getMerchantCode())
        signStr.append(req.getOrderId())
        signStr.append(transAmt)
        signStr.append(merchantaccount.getPrivateKey())
        String sign = MD5.md5(signStr.toString())

        StringBuilder sb = new StringBuilder()
        sb.append(merchantaccount.getPayUrl()).append("/orgReq/cashierPayH5?")
        sb.append("requestNo=").append(requestNo).append("&")
        sb.append("version=").append("V1.0").append("&")
        sb.append("productId=").append("SY01").append("&")
        sb.append("transId=").append("01").append("&")
        sb.append("merNo=").append(merchantaccount.getMerchantCode()).append("&")
        sb.append("orderDate=").append(DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDD)).append("&")
        sb.append("orderNo=").append(req.getOrderId()).append("&")
        sb.append("notifyUrl=").append(merchantaccount.getNotifyUrl() + merchant.getId()).append("&")
        sb.append("transAmt=").append(transAmt).append("&")
        sb.append("signature=").append(sign)
        log.info("JuheWeixinScript_PrepareToWangyin_Prepare:{}", sb.toString())
        result.setRedirectUrl(sb.toString())
    }


    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.rechargeMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlRechargeMapper) as GlRechargeMapper
        log.info("JuheWeixinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.rechargeMapper = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeMapper) as GlRechargeMapper
        GlRecharge recharge = rechargeMapper.selectByPrimaryKey(orderId)
        if (recharge == null) {
            return null
        }
        String requestNo = System.currentTimeMillis() + ""//交易流水号
        String transId = "04"

        StringBuilder signStr = new StringBuilder()
        signStr.append(requestNo)
        signStr.append(transId)
        signStr.append(account.getMerchantCode())
        signStr.append(orderId)
        signStr.append(account.getPrivateKey())
        String sign = MD5.md5(signStr.toString())

        Map<String, String> params = new HashMap<String, String>()
        params.put("requestNo", requestNo)
        params.put("version", "V1.0")
        params.put("transId", transId)
        params.put("merNo", account.getMerchantCode())
        params.put("orderDate", DateUtils.format(recharge.getCreateDate(), DateUtils.YYYYMMDD))
        params.put("orderNo", orderId)
        params.put("orderAmt", (recharge.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("signature", sign)

        log.info("JuheWeixinScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orgReq/trQue", params, requestHeader)
        log.info("JuheWeixinScript_Query_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        if ("0000" == (json.getString("respCode")) && "0000" == (json.getString("origRespCode"))) {//原交易应答码  交易成功：0000
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(recharge.getAmount())
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
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
                .channelId(PaymentMerchantEnum.JUHEWEIXIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JUHEWEIXIN_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}
