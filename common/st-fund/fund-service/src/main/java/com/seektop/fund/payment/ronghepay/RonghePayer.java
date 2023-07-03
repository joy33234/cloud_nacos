package com.seektop.fund.payment.ronghepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.RONGHEPAY + "")
public class RonghePayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() ||
                FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() ||
                FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            String payType;
            if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
                payType = "1";
            } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
                payType = "3";
            } else {
                payType = "0";
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", account.getMerchantCode());
        params.put("out_trade_no", req.getOrderId());
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        params.put("notice_url", account.getNotifyUrl() + merchant.getId());
        params.put("client_ip", "0.0.0.0");
        params.put("pay_type", payType);
        params.put("nonce_str", System.currentTimeMillis() + "");
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        params.put("success_url", "http://www.baidu.com");
        params.put("error_url", "http://www.baidu.com");
        log.info("RonghePayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.RONGHE_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.RONGHE_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/v1/order/create", params, requestHeader);
        log.info("RonghePayer_recharge_prepare_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSON.parseObject(resp);
        if (!"0".equals(json.getString("code"))) {
            throw new RuntimeException("创建订单失败");
        }
        result.setRedirectUrl(json.getJSONObject("extra").getString("pay_url"));
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("RonghePayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("out_trade_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", account.getMerchantCode());
        params.put("out_trade_no", orderId);
        params.put("nonce_str", System.currentTimeMillis() + "");
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("RonghePayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.RONGHE_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.RONGHE_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/v1/order/query", params, requestHeader);
        log.info("RonghePayer_query_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resp);
        if ("0".equals(json.getString("code")) && "1".equals(json.getJSONObject("extra").getString("status"))) {
            json = json.getJSONObject("extra");
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("transaction_id"));
            return pay;
        }
        return null;
    }
}
