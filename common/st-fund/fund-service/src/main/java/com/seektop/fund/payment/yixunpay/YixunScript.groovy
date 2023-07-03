package com.seektop.fund.payment.yixunpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.business.recharge.GlRechargeBusiness
import com.seektop.fund.model.*
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.ResourceEnum
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class YixunScript {

    private static final Logger log = LoggerFactory.getLogger(YixunScript.class)

    private OkHttpUtil okHttpUtil
    private GlRechargeBusiness rechargeService
    private GlPaymentChannelBankBusiness channelBankBusiness

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[5], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        String payType = ""
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "alipay_scand0"
            } else {
                payType = "alipay_H5d0"
            }
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "weixin_scand0"
            } else {
                payType = "weixin_H5d0"
            }
        }
        prepareToScan(merchant, account, req, result, payType)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>()
        params.put("pay_type", payType)
        params.put("unique_id", account.getMerchantCode())
        params.put("price", (req.getAmount() * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("notice_url", account.getNotifyUrl() + merchant.getId())
        params.put("order_number", req.getOrderId())
        params.put("return_url", account.getNotifyUrl() + merchant.getId())
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("YixunScript_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.YIXUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIXUN_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String resp = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/getPayUrl.html", params, requestHeader)
        log.info("YixunScript_prepare_resp:{}", resp)
        JSONObject json = this.checkResponse(resp, false)
        if (json == null) {
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败")
            return
        }
        result.setRedirectUrl(json.getString("data"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[4], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        log.info("YixunScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNum")
        if (null != orderId && "" != (orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        GlRecharge recharge = rechargeService.findById(orderId)
        if (recharge == null) {
            return null
        }
        Map<String, String> params = new HashMap<>()
        params.put("price", recharge.getAmount() + "")
        params.put("unique_id", account.getMerchantCode())
        params.put("Order_id", orderId)
        String toSign = MD5.toAscii(params)
        toSign += "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("YixunScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.YIXUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIXUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String resp = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/OrderQuery.html", params, requestHeader)
        log.info("YixunScript_query_resp:{}", resp)
        if (StringUtils.isEmpty(resp)) {
            return null
        }
        JSONObject json = this.checkResponse(resp, true)
        if ("1" == (json.getString("ispay"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("goods_price").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId("")
            return pay
        }
        return null
    }


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        WithdrawResult result = new WithdrawResult()
        Map<String, Object> payCodeMap = getPayCode(account)
        BigDecimal amount = (BigDecimal) payCodeMap.get("amount")

        if ((req.getAmount() <=> amount) > 1) {
            result.setValid(false)
            result.setMessage("商户余额不足.")
            return result
        }

        Map<String, String> params = new HashMap<String, String>()
        params.put("price", (req.getAmount().subtract(req.getFee()) * new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("unique_id", account.getMerchantCode())
        params.put("order_number", req.getOrderId())
        params.put("api_type", payCodeMap.get("api_type").toString())
        params.put("pay_type", payCodeMap.get("pay_type").toString())
        params.put("cardname", channelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))//银行名称
        params.put("bank_code", channelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))//银行卡编号
        params.put("cardno", req.getCardNo())//银行卡编号
        params.put("name", req.getName())//持卡人姓名
        params.put("notice_url", account.getNotifyUrl() + account.getMerchantId())//持卡人姓名
        params.put("subcardname", "上海市")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("YixunScript_Transfer_paramMap:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/Substitute.html", params)
        log.info("YixunScript_Transfer_resStr:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        String message = StringUtils.unicodeToChinese(json.getString("data"))
        if ("200" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(message)
            return result
        }
        result.setValid(true)
        result.setMessage(message)
        result.setThirdOrderId("")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        log.info("YixunScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("orderNum")
        if (org.springframework.util.StringUtils.isEmpty(orderId)) {
            log.info("YixunScript_Notify_Exception:{}", JSON.toJSONString(resMap))
            return null
        }
        return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        Map<String, String> params = new HashMap<String, String>()
        params.put("unique_id", merchant.getMerchantCode())
        params.put("Order_id", orderId)//订单编号

        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))
        log.info("YixunScript_Transfer_Query_paramMap:{}", params)
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/PayView/Index/SubstituteResult.html", params)
        log.info("YixunScript_Transfer_Query_resStr:{}", resStr)
        JSONObject json = this.checkResponse(resStr, true)
        if (json == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("goods_price"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")
        notify.setSuccessTime(json.getDate("grant_time"))
        if (json.getString("is_forward") == ("1")) {//0-处理中，1-代付成功（其余均为失败）   --  0成功，1失败,2处理中
            notify.setStatus(0)
        } else if (json.getString("is_forward") == ("0")) {
            notify.setStatus(2)
        } else {
            notify.setStatus(1)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        this.channelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.rechargeService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        BigDecimal result = BigDecimal.ZERO//余额yi
        JSONArray array = this.getBalanceData(merchant)

        if (array == null || array.size() <= 0) {
            return result
        }

        for (int i = 0; i < array.size(); i++) {
            BigDecimal amount = array.getJSONObject(i).getBigDecimal("balance")
            result = result.add(amount)
        }
        return result
    }

    /**
     * 查询余额接口
     *
     * @param merchant
     * @return
     */
    private JSONArray getBalanceData(GlWithdrawMerchantAccount merchant) {
        Map<String, String> params = new HashMap<>()
        params.put("unique_id", merchant.getMerchantCode())
        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey()
        params.put("sign", MD5.md5(toSign))

        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/PayView/Index/BalanceInquire.html", params)
        log.info("YixunScript_Balance_Query_resStr:{}", resStr)
        JSONObject json = this.checkResponse(resStr, true)
        if (json == null) {
            return null
        }
        String balanceArr = json.getString("payBalance")
        JSONArray array = JSON.parseArray(balanceArr)
        return array
    }


    /**
     * 获取代付渠道与编码
     *
     * @param merchant
     * @return
     */
    public Map<String, Object> getPayCode(GlWithdrawMerchantAccount merchant) {
        Map<String, Object> map = new HashMap<>()
        JSONArray array = this.getBalanceData(merchant)

        if (array == null || array.size() <= 0) {
            return null
        }
        BigDecimal maxAmount = BigDecimal.ZERO
        map.put("amount", maxAmount)
        for (int i = 0; i < array.size(); i++) {
            BigDecimal amount = array.getJSONObject(i).getBigDecimal("balance")
            if ((amount <=> maxAmount) > 0) {
                map.put("api_type", array.getJSONObject(i).getString("api_type"))
                map.put("pay_type", array.getJSONObject(i).getString("pay_type"))
                map.put("amount", amount)
                maxAmount = amount
            }
        }
        return map
    }

    /**
     * 检验返回数据
     *
     * @param response
     * @return
     */
    private JSONObject checkResponse(String response, boolean data) {
        if (StringUtils.isEmpty(response)) {
            return null
        }
        JSONObject json = JSON.parseObject(response)
        if (json == null || "200" != (json.getString("code"))) {
            return null
        }
        if (data) {
            String dataStr = json.get("data").toString()
            JSONObject dataJson = JSON.parseObject(dataStr)
            return dataJson
        }
        return json
    }
}
