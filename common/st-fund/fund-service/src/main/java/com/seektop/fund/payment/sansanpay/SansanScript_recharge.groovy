package com.seektop.fund.payment.sansanpay

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
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc sansan支付
 * @date 2021-04-15
 * @auth joy
 */
public class SansanScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(SansanScript_recharge.class)

    private OkHttpUtil okHttpUtil


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            payType = "1"//支付宝转帐，
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "3"//卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("app_id", account.getMerchantCode())
        params.put("format", "JSON")
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS);

        log.info("key:{}",account.getPrivateKey())
        log.info("merchantcode:{}",account.getMerchantCode())
        log.info("stamptime:{}",stamptime)
        log.info("stamptime:{}",stamptime)
        params.put("sign", MD5.md5(account.getPrivateKey() + account.getMerchantCode() + stamptime));
        params.put("stamptime", stamptime)
        params.put("version", "1.0")
        params.put("notify_url", account.getNotifyUrl() + merchant.getId())

        //业务参数
        params.put("out_trade_no",req.getOrderId())
        params.put("name",req.getFromCardUserName())
        params.put("pay_type", payType)
        params.put("total_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        params.put("biz_content", JSON.toJSONString(params))


        log.info("SansanScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/addons/payment/order/api_info", JSON.toJSONString(params), requestHeader)
        log.info("SansanScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "1" != json.getString("code") || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data").getJSONObject("info");
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(dataJSON.getString("name"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(dataJSON.getString("bank"))
        bankInfo.setBankBranchName(dataJSON.getString("address"))
        bankInfo.setCardNo(dataJSON.getString("number"))
        result.setBankInfo(bankInfo)
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("SansanScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("biz_content"))
        if (StringUtils.isNotEmpty(json.getString("out_trade_no"))) {
            return this.payQuery(okHttpUtil, account, json.getString("out_trade_no"), args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String


        Map<String, String> params = new LinkedHashMap<>()
        //公共参数
        params.put("app_id", account.getMerchantCode())
        params.put("format", "JSON")
        params.put("charset", "utf-8")
        String stamptime = DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        params.put("sign", MD5.md5(account.getPrivateKey() + account.getMerchantCode() + stamptime));
        params.put("stamptime", stamptime)
        params.put("version", "1.0")
        params.put("notify_url", "")
        //业务参数
        params.put("order_id", orderId)

        params.put("biz_content", JSON.toJSONString(params))

        log.info("SansanScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/addons/payment/order/api_get_order", JSON.toJSONString(params), requestHeader)
        log.info("SansanScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "1" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data").getJSONArray("list").get(0)
        // 支付状态:0=待支付,1=支付成功,2=支付成功,3=已取消,4=已关闭
        if ("1" == (dataJSON.getString("states")) || "2" == (dataJSON.getString("states"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
            pay.setRsp("{\"code\":1,\"msg\":\"Success\"}")
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