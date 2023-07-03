package com.seektop.fund.payment.pay168

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
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

class Payer168USDTScript {

    private static final Logger log = LoggerFactory.getLogger(Payer168USDTScript.class)

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
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey() // 商家密钥
        //参与签名字段
        String pay_memberid = payment.getMerchantCode()//商户号
        String pay_orderid = req.getOrderId()//订单号
        String pay_applydate = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)//提交时间
        String pay_bankcode = "6186" //USDT编码
        String pay_notifyurl = payment.getNotifyUrl() + merchant.getId()//回调地址
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

        paramMap.put("pay_md5sign", pay_md5sign)
        paramMap.put("format", "json")//返回数据格式
        paramMap.put("pay_productname", pay_productname)
        paramMap.put("return_beneficiary_account", "true")//是否返回收款信息
        log.info("Payer168USDTScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html", paramMap, requestHeader)
        log.info("Payer168USDTScript_Prepare_restr: {}", restr)
        JSONObject json = JSON.parseObject(restr)

        if (null == json) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("status") != "ok") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
        JSONObject dataJSON = json.getJSONObject("data")
        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setOwner(dataJSON.getString("bank_owner"))
        blockInfo.setDigitalAmount(dataJSON.getBigDecimal("real_price"))
        blockInfo.setProtocol(dataJSON.getString("bank_from"))
        blockInfo.setBlockAddress(dataJSON.getString("bank_no"))
        blockInfo.setRate(dataJSON.getBigDecimal("rate"))
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
        result.setBlockInfo(blockInfo)
        result.setThirdOrderId(dataJSON.getString("transaction_id"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("Payer168USDTScript_Notify_resMap:{}", JSON.toJSONString(resMap))
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
        log.info("Payer168USDTScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())

        if (queryUrl.contains("https")) {
            queryUrl = queryUrl.replace("https", "http")
        }

        String result = okHttpUtil.post(queryUrl, paramMap, requestHeader)
        log.info("Payer168USDTScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null) {
            return null
        }
        String returncode = json.getString("returncode")
        String trade_state = json.getString("trade_state")
        if (("00" != returncode) || "SUCCESS" != trade_state) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("amount"))//paid_amount实际支付金额
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(json.getString("transaction_id"))
        pay.setRsp("OK")
        return pay
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
        return BigDecimal.ZERO
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

    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }
}