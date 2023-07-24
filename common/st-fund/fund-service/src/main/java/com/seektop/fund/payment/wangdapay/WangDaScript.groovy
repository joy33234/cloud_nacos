package com.seektop.fund.payment.wangdapay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 万达支付
 *
 */

class WangDaScript {

    private static final Logger log = LoggerFactory.getLogger(WangDaScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

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
        String bankcode = ""
        String url = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            bankcode = "WANGYINBANK"
            url = "/get_banktransfers"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            bankcode = "ALIPAY"
            url = "/api_desposits "
        }
        if (StringUtils.isNotEmpty(bankcode)) {
            prepareScan(merchant, payment, req, result, bankcode, url)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String bankcode, String url) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("CustomerId", req.getOrderId());
            params.put("Mode", "8");
            params.put("BankCode", bankcode);
            params.put("Message", "CZ");
            params.put("Money", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            params.put("UserId", payment.getMerchantCode());
            params.put("CallBackUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("ReturnUrl", payment.getResultUrl() + merchant.getId());

            String toSign = MD5.toAscii(params) + "&Key=" + payment.getPrivateKey();
            params.put("Sign", MD5.md5(toSign));

            params.put("Nickname", "recharge");
            params.put("Realname", req.getFromCardUserName());
            log.info("WangDaScript_Prepare_Params = {}", JSON.toJSONString(params));

            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.post(payment.getPayUrl() + url, params, requestHeader)
            log.info("WangDaScript_Prepare_resStr = {}", restr)

            JSONObject json = JSON.parseObject(restr);
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("code") != "200") {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(json.getString("msg"))
                return
            }
            JSONObject dataJSON = json.getJSONObject("data");
            if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("payUrl"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(json.getString("msg"))
                return
            }
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(dataJSON.getString("payUrl"));
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
        log.info("WangDaScript_Notify_resMap = {}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("customerId");
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
        Map<String, String> params = new HashMap<String, String>();
        params.put("UserId", account.getMerchantCode());
        params.put("OrderType", "1");//1:充值订单 2:提现订单
        params.put("CustomerId", orderId);

        String toSign = MD5.toAscii(params) + "&Key=" + account.getPrivateKey();
        params.put("Sign", MD5.md5(toSign));

        log.info("WangDaScript_Query_reqMap = {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api_query", params, requestHeader)
        log.info("WangDaScript_Query_resStr = {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null && json.getString("code") != "200") {
            return null;
        }

        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON != null && "1" == dataJSON.getString("status")) {// 支付状态【1正常支付】【0支付异常】
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("money").setScale(0, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(dataJSON.getString("orderId"))
            pay.setRsp("200")
            return pay
        }
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
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER) {
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
        return FundConstant.ShowType.NORMAL
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