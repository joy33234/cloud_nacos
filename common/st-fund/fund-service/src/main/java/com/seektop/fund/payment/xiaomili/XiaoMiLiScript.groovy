package com.seektop.fund.payment.xiaomili

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 小米粒支付
 *
 */
public class XiaoMiLiScript {

    private static final Logger log = LoggerFactory.getLogger(XiaoMiLiScript.class)

    private OkHttpUtil okHttpUtil

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
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "3"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "4"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>()
            DataContentParms.put("pay_memberid", payment.getMerchantCode())
            DataContentParms.put("pay_bankcode", payType)//支付编码
            DataContentParms.put("pay_orderid", req.getOrderId())
            DataContentParms.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
            DataContentParms.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            DataContentParms.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId())
            DataContentParms.put("pay_callbackurl", payment.getNotifyUrl() + merchant.getId())

            String toSign = MD5.toAscii(DataContentParms) + "&key=" + payment.getPrivateKey()
            DataContentParms.put("pay_md5sign", MD5.md5(toSign).toUpperCase())
            DataContentParms.put("pay_productname", "recharge")//商品名称


            log.info("XiaoMiLiScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", DataContentParms, requestHeader)
            log.info("XiaoMiLiScript_Prepare_resStr:{}", restr)

            JSONObject json = JSON.parseObject(restr)
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("status") != ("success")) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }
            JSONObject dataJSON = json.getJSONObject("data")
            if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("payurl"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(dataJSON.getString("payurl"))
        } catch (Exception e) {
            e.printStackTrace()
        }
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
        log.info("XiaoMiLiScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid)
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("pay_memberid", account.getMerchantCode())
        DataContentParms.put("pay_orderid", orderId)

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey()
        DataContentParms.put("pay_md5sign", MD5.md5(toSign).toUpperCase());

        log.info("XiaoMiLiScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", DataContentParms, requestHeader)
        log.info("XiaoMiLiScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        //NOTPAY-未支付 SUCCESS已支付
        if ("00" == (json.getString("returncode")) && "SUCCESS" == (json.getString("trade_state"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(0, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("OK")
            pay.setThirdOrderId(json.getString("transaction_id"))
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

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}