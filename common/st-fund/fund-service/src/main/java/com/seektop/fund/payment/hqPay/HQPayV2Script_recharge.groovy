package com.seektop.fund.payment.hqPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.fasterxml.jackson.databind.ObjectMapper
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
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

import java.math.RoundingMode

/**
 * @desc HQ支付
 * @auth matt
 * @date 2022-04-06
 */
public class HQPayV2Script_recharge {

    private static final Logger log = LoggerFactory.getLogger(HQPayV2Script_recharge.class)
    private OkHttpUtil okHttpUtil
    private ObjectMapper objectMapper = new ObjectMapper();
    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        prepareToScan(merchant, account, req, result)
    }

    boolean isSuccessResponse(JSONObject response) {
        return Optional.ofNullable(response)
                .map({ j -> j.getString("success") })
                .filter("true".&equals)
                .isPresent();
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        BigDecimal amount= req.getAmount();

        params.put("amount",  amount.setScale(2, RoundingMode.DOWN).toString());
        params.put("callback", account.getNotifyUrl()+ merchant.getId());
        params.put("depositRealname", req.getFromCardUserName())
        params.put("merchant", account.getMerchantCode())
        params.put("paymentReference",req.getOrderId())
        params.put("paymentType","6")
        params.put("username", String.valueOf(req.getUserId()))

        log.info("HQPayScript_recharge:okHttpUtil.post:[{}]", params)
        String code = MD5.toSign(params) + "&key=" + account.getPrivateKey();
        String sign = MD5.md5(code).toUpperCase()
        params.put("sign",sign)
        params.put("useCounter", "false")

        String payUrl = account.getPayUrl() + "/api/deposit/page";

        log.info("HQPayScript_recharge:url:[{}] okHttpUtil.post:[{}]", payUrl, params)
        String resStr = okHttpUtil.post(
                payUrl ,
                params,
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        )
        log.info("HQPayScript_recharge_prepare_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (!isSuccessResponse(json) || StringUtils.isEmpty(json.getString("data"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject acct = json.getJSONObject("data")
        if (acct.containsKey("recAccount") && acct.containsKey("recRealname") && acct.containsKey("recBank")) {
            BankInfo bankInfo = new BankInfo()
            bankInfo.setName(acct.getString("recRealname"))
            bankInfo.setBankName(acct.getString("recBank"))
            bankInfo.setCardNo(acct.getString("recAccount"))
            result.setBankInfo(bankInfo)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
        }
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> raw = args[3] as Map<String, String>
        log.info("HQPayScript_notify_resp:{}", raw)
        String orderId = raw.get("paymentReference")
        return this.payQuery(okHttpUtil, account, orderId, raw.get("amount"))
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String paidAmount = args[3] as String
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchant",account.getMerchantCode())
        params.put("paymentReference", orderId)
        params.put("sign", MD5.md5(MD5.toSign(params) + "&key=" + account.getPrivateKey()).toUpperCase())

        log.info("HQPayScript_query_params:{}", JSON.toJSONString(params))
        String resStr = okHttpUtil.post(
                account.getPayUrl() + "/api/deal/query" ,
                params,
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        )
        log.info("HQPayScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)

        // 0，待处理。1，成功。2，失败。
        if (isSuccessResponse(json) && json.getJSONObject("data") != null) {
            JSONObject data = json.getJSONObject("data");
            if ("1".equals(data.getString("statusSt"))) {
                RechargeNotify pay = new RechargeNotify()
                pay.setAmount(new BigDecimal(paidAmount).setScale(2, RoundingMode.DOWN))
                pay.setFee(BigDecimal.ZERO)
                pay.setOrderId(orderId)
                pay.setRsp("success")
                return pay
            } else {
                return null;
            }
        } else {
            return null;
        }
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