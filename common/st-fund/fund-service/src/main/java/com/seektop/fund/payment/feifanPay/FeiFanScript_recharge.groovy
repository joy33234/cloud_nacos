package com.seektop.fund.payment.feifanPay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 菲凡支付
 * @auth joy
 * @date 2021-07-25
 */

class FeiFanScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(FeiFanScript_recharge.class)

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

        String bankcode = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            bankcode = "935"
        }
        if (StringUtils.isNotEmpty(bankcode)) {
            prepareScan(merchant, payment, req, result, bankcode)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String bankcode) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("pay_memberid", payment.getMerchantCode())
        params.put("pay_orderid", req.getOrderId())
        params.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("pay_bankcode", bankcode)//卡卡
        params.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId())
        params.put("pay_callbackurl", payment.getNotifyUrl() + merchant.getId())
        params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String toSign = MD5.toAscii(params) + "&key=" + payment.getPrivateKey()
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        params.put("pay_md5sign", pay_md5sign)

        log.info("FeiFanScript_Prepare_Params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", params,  requestHeader)
        log.info("FeiFanScript_Prepare_resStr:{}", restr)

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (StringUtils.isEmpty(json.getString("cardNo"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("返回数据错误")
            return
        }
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(json.getString("name"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(json.getString("bankName"))
        bankInfo.setBankBranchName(json.getString("city"))
        bankInfo.setCardNo(json.getString("cardNo"))
        result.setBankInfo(bankInfo)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("FeiFanScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderid;
        if (json == null) {
            orderid = resMap.get("orderid");
        } else {
            orderid = json.getString("orderid");
        }
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid)
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("pay_memberid", account.getMerchantCode())
        params.put("pay_orderid", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        params.put("pay_md5sign", pay_md5sign)

        log.info("FeiFanScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", params,  requestHeader)
        log.info("FeiFanScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || json.getString("returncode") != "00") {
            return null
        }
        //支付状态 订单状态: NOTPAY-未支付 SUCCESS已支付
        if ("SUCCESS" == json.getString("trade_state")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId.toUpperCase())
            pay.setRsp("OK")
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
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
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