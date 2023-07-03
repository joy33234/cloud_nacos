package com.seektop.fund.payment.futailangpay

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
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class FuTaiLangScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(FuTaiLangScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        String paymentType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            paymentType = "wyyhk"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            paymentType = "mybank"
        }
        if (StringUtils.isNotEmpty(paymentType)) {
            prepareScan(merchant, payment, req, result, paymentType)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String pay_bankcode) {
        String keyValue = payment.getPrivateKey() // 商家密钥

        String pay_memberid = payment.getMerchantCode()
        String pay_orderid = req.getOrderId()
        String pay_applydate = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)
        String pay_notifyurl = payment.getNotifyUrl() + merchant.getId()
        String pay_callbackurl = payment.getResultUrl() + merchant.getId()
        String pay_amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString()
        String pay_productname = "CZ"//商品名称  必填不参与签名

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_orderid", pay_orderid)
        paramMap.put("pay_applydate", pay_applydate)
        paramMap.put("pay_bankcode", pay_bankcode)
        paramMap.put("pay_notifyurl", pay_notifyurl)
        paramMap.put("pay_amount", pay_amount)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()

        // 网关及支付宝转银联需要实名
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.UNION_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("pay_username", req.getFromCardUserName())
        }
        paramMap.put("pay_md5sign", pay_md5sign)
        paramMap.put("pay_productname", pay_productname)
        paramMap.put("format", "json")//返回数据格式
        paramMap.put("return_beneficiary_account", "true")//是否返回收款信息

        log.info("FuTaiLangScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())

        String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", paramMap, 10L, requestHeader)
        JSONObject json = JSON.parseObject(restr)
        log.info("FuTaiLangScript_Prepare_Resp: {}", json)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("status") != "ok") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        JSONObject dataJSON = json.getJSONObject("data")

        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        BankInfo bankInfo = new BankInfo()
        bankInfo.setName(dataJSON.getString("bank_owner"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank_name"))
        bankInfo.setBankBranchName(dataJSON.getString("bank_from"))
        bankInfo.setCardNo(dataJSON.getString("bank_no"))
        result.setBankInfo(bankInfo)

        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        result.setAmount(dataJSON.getBigDecimal("real_price"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("FuTaiLangScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("orderid")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String keyValue = payment.getPrivateKey() // 商家密钥

        String pay_memberid = payment.getMerchantCode()
        String pay_orderid = orderId

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("pay_memberid", pay_memberid)
        paramMap.put("pay_orderid", pay_orderid)

        String toSign = MD5.toAscii(paramMap)
        toSign = toSign + "&key=" + keyValue
        String pay_md5sign = MD5.md5(toSign).toUpperCase()
        paramMap.put("pay_md5sign", pay_md5sign)
        String queryUrl = payment.getPayUrl() + "/Pay_Trade_query.html"
        log.info("FuTaiLangScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())

        String result = okHttpUtil.post(queryUrl, paramMap, 10L, requestHeader)
        log.info("FuTaiLangScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        String returncode = json.getString("returncode")
        String trade_state = json.getString("trade_state")
        if (("00" != returncode) || "SUCCESS" != trade_state) {
            return null
        }
        //支付完成：1。 等待付款：2。 支付关闭：3。
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("paid_amount").setScale(2, RoundingMode.DOWN))//paid_amount实际支付金额
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        return pay
    }


    void cancel(Object[] args) throws GlobalException {

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
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
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
        return FundConstant.ShowType.NORMAL
    }
}