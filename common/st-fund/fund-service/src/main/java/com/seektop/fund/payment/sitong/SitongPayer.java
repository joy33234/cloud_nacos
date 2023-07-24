package com.seektop.fund.payment.sitong;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 四通支付：网银支付
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.SITONGPAY + "")
public class SitongPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;
    @Resource
    private OkHttpUtil okHttpUtil;


    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        String[] merchantValue = payment.getMerchantCode().split("\\|\\|");
        String orderAmount = req.getAmount().setScale(2, RoundingMode.DOWN).toString();
        String outTradeNo = req.getOrderId();
        String pType = "GS0005";
        String orderTime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
        String storeInfo = "CZ";
        String orderIp = req.getIp();
        String returnUrl = payment.getResultUrl() + merchant.getId();
        String notifyUrl = payment.getNotifyUrl() + merchant.getId();
        String randomStr = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", merchantValue[0]);
        paramMap.put("orderAmount", orderAmount);
        paramMap.put("outTradeNo", outTradeNo);
        paramMap.put("pType", pType);
        paramMap.put("orderTime", orderTime);
        paramMap.put("storeInfo", storeInfo);
        paramMap.put("orderIp", orderIp);
        paramMap.put("returnUrl", returnUrl);
        paramMap.put("notifyUrl", notifyUrl);
        paramMap.put("randomStr", randomStr);
        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        String sign = MD5.md5(toSign).toUpperCase();
        paramMap.put("sign", sign);
        log.info("SiTongPayer_Prepare_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.SITONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SITONG_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resp = okHttpUtil.post(payment.getPayUrl() + "/cnpPay/placeOrder", paramMap, requestHeader);
        log.info("SiTongPayer_Prepare_resStr: {}", resp);
        JSONObject json = JSONObject.parseObject(resp);
        if (!"0000".equals(json.getString("resultCode"))) {
            throw new RuntimeException("创建订单失败");
        }
        if (json.getString("payMessage").contains("<script>")) {
            result.setMessage(HtmlTemplateUtils.getQRCode(json.getString("payMessage").split("\"")[1].split("\"")[0]));
        } else {
            result.setMessage(HtmlTemplateUtils.getQRCode(json.getString("payMessage")));
        }
    }

    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        log.info("SitongPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String merOrderNo = resMap.get("outTradeNo");// 商户订单号
        if (StringUtils.isNotEmpty(merOrderNo)) {
            return query(payment, merOrderNo);
        }
        return null;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        Map<String, String> paramMap = new HashMap<>();
        String[] merchantValue = payment.getMerchantCode().split("\\|\\|");
        paramMap.put("appid", merchantValue[0]);
        paramMap.put("outTradeNo", orderId);
        paramMap.put("randomStr", UUID.randomUUID().toString().replace("-", ""));
        String SignInfo = MD5.toAscii(paramMap);
        SignInfo = SignInfo + "&key=" + keyValue;
        String sign = MD5.md5(SignInfo).toUpperCase();
        paramMap.put("sign", sign);
        log.info("SiTongPayer_Query_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SITONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SITONG_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String result = okHttpUtil.post(payment.getPayUrl() + "/query/payOrder", paramMap, requestHeader);
        log.info("SiTongPayer_Query_resStr: {}", result);
        JSONObject json = JSON.parseObject(result);
        if (json == null || json.getString("resultCode") == null) {
            return null;
        }
        String orderStatus = json.getString("orderStatus");
        if (!"SUCCESS".equals(orderStatus)) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(json.getBigDecimal("orderAmount"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("trxNo"));
        return pay;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥
        String[] merchantValue = merchantAccount.getMerchantCode().split("\\|\\|");
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", merchantValue[0]);
        paramMap.put("outTradeNo", req.getOrderId());
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        paramMap.put("proxyType", "D0");
        paramMap.put("bankAccountType", "PRIVATE_DEBIT_ACCOUNT");
        paramMap.put("receiverName", req.getName());
        paramMap.put("bankCode", paymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        paramMap.put("receiverAccountNo", req.getCardNo());
        paramMap.put("randomStr", UUID.randomUUID().toString().replace("-", ""));
        String signInfo = MD5.toAscii(paramMap);
        signInfo = signInfo + "&key=" + keyValue;
        String sign = MD5.md5(signInfo).toUpperCase();
        paramMap.put("sign", sign);
        log.info("SiTongPayer_Transfer_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.SITONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SITONG_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/payForAnother/paid", paramMap, requestHeader);
        log.info("SiTongPayer_Transfer_resStr: {}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(paramMap));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"0000".equals(json.getString("resultCode")) || !"9996".equals(json.getString("resultCode"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("errMsg"));
            return result;
        }

        JSONObject body = json.getJSONObject("body");
        String TransStatus = body.getString("remitStatus");
        result.setValid("REMIT_SUCCESS".equals(TransStatus) || ("REMITTING").equals(TransStatus));
        result.setMessage(json.getString("errMsg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("SitongPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        String merOrderNo = resMap.get("outTradeNo");// 商户订单号
        if (StringUtils.isNotEmpty(merOrderNo)) {
            return doTransferQuery(merchant, merOrderNo);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String keyValue = merchant.getPrivateKey(); // 商家密钥
        String[] merchantValue = merchant.getMerchantCode().split("\\|\\|");
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", merchantValue[0]);
        paramMap.put("outTradeNo", orderId);
        paramMap.put("randomStr", UUID.randomUUID().toString().replace("-", ""));
        String signInfo = MD5.toAscii(paramMap);
        signInfo = signInfo + "&key=" + keyValue;
        String sign = MD5.md5(signInfo).toUpperCase();
        paramMap.put("sign", sign);
        log.info("SiTongPayer_TransferQuery_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SITONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SITONG_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/payForAnother/query", paramMap, requestHeader);
        log.info("SiTongPayer_TransferQuery_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getString("resultCode") == null || !"0000".equals(json.getString("resultCode"))) {
            return null;
        }
        String tradeStatus = json.getString("remitStatus");
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(new BigDecimal(json.getString("settAmount")));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark(json.getString("errMsg"));
        if (tradeStatus.equals("REMIT_SUCCESS")) {
            notify.setStatus(0);
        } else if (tradeStatus.equals("REMIT_FAIL")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥
        String[] merchant = merchantAccount.getMerchantCode().split("\\|\\|");
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", merchant[0]);//应用ID
        paramMap.put("merchantNo", merchant[1]);//商户编号
        paramMap.put("randomStr", UUID.randomUUID().toString().replace("-", ""));
        String signInfo = MD5.toAscii(paramMap);
        signInfo = signInfo + "&key=" + keyValue;
        String sign = MD5.md5(signInfo).toUpperCase();
        paramMap.put("sign", sign);
        log.info("SiTongPayer_QueryBalance_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.SITONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SITONG_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/query/balance", paramMap, requestHeader);
        log.info("SiTongPayer_QueryBalance_resStr: {}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getString("resultCode") == null || !"0000".equals(json.getString("resultCode"))) {
            return BigDecimal.ZERO;
        }
        JSONObject body = json.getJSONObject("body");
        BigDecimal Balance = body.getBigDecimal("totalBalance");
        return Balance == null ? BigDecimal.ZERO : Balance;
    }

}
