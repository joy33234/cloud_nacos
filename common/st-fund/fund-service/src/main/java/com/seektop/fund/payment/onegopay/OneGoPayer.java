package com.seektop.fund.payment.onegopay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.constant.FundConstant;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.GlPaymentRechargeHandler;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.RechargeNotify;
import com.seektop.fund.payment.RechargePrepareDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * OneGo支付
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.ONEGOPAY + "")
public class OneGoPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            prepareToWangying(merchant, payment, req, result);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            prepareToAliPay(merchant, payment, req, result);
        }
    }

    private void prepareToAliPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            JSONObject data = new JSONObject();
            data.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN));
            data.put("out_trade_no", req.getOrderId());
            data.put("notify_url", payment.getNotifyUrl() + merchant.getId());
            if (req.getClientType() != 0) {
                data.put("return_url", payment.getResultUrl() + merchant.getId());
            }

            log.info("OneGoPay_Prepare_reqMap: {}", data.toJSONString());
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(payment.getPayUrl());
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Authorization", "Bearer " + payment.getPrivateKey());
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("OneGoPay_Prepare_resStr: {}", resStr);

            JSONObject resObj = JSON.parseObject(resStr);
            String uri = resObj.getString("uri");
            if (StringUtils.isEmpty(uri)) {
                throw new RuntimeException("创建订单失败");
            }
            result.setRedirectUrl(uri);
        } catch (Exception e) {
            log.error("OneGoPayer_Prepare_Error", e);
            throw new RuntimeException("创建订单失败");
        }
    }

    private void prepareToWangying(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        JSONObject data = new JSONObject();
        data.put("bank", paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()));
        data.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN));
        data.put("out_trade_no", req.getOrderId());
        data.put("notify_url", payment.getNotifyUrl() + merchant.getId());

        log.info("OneGoPay_Prepare_reqMap: {}", data.toJSONString());
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(payment.getPayUrl());
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Authorization", "Bearer " + payment.getPrivateKey());
        httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
        CloseableHttpResponse response2 = null;
        try {
            response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("OneGoPay_Prepare_resStr: {}", resStr);
            JSONObject resObj = JSON.parseObject(resStr);
            String uri = resObj.getString("uri");
            if (StringUtils.isEmpty(uri)) {
                throw new RuntimeException("创建订单失败");
            }
            result.setRedirectUrl(uri);
        } catch (IOException e) {
            log.error("OneGoPayer_Prepare_Error", e);
            throw new RuntimeException("创建订单失败");
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
        log.info("OneGoPayer_Notify_resMap: {}", JSON.toJSONString(resMap));
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        JSONObject jsonObject = JSON.parseObject(reqBody);
        String orderId = jsonObject.getString("out_trade_no");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return query(payment, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpGet httpPost = new HttpGet(payment.getPayUrl() + "/" + orderId);
            httpPost.addHeader("Authorization", "Bearer " + payment.getPrivateKey());
            httpPost.addHeader("Content-Type", "application/json");
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("OneGoPayer_Query_resStr: {}", resStr);
            JSONObject resObj = JSON.parseObject(resStr);
            if (null == resObj) {
                return null;
            }
            String status = resObj.getString("status");
            String out_trade_no = resObj.getString("out_trade_no");
            if (StringUtils.isNotEmpty(status) && StringUtils.isNotEmpty(out_trade_no) && status.equals("success") && out_trade_no.equals(orderId)) {
                RechargeNotify pay = new RechargeNotify();
                pay.setOrderId(resObj.getString("out_trade_no"));
                pay.setAmount(resObj.getBigDecimal("amount"));
                pay.setFee(BigDecimal.ZERO);
                pay.setThirdOrderId(resObj.getString("trade_no"));
                return pay;
            }
        } catch (Exception e) {
            log.error("onego payer error", e);
        }
        return null;
    }

    /**
     * 解析页面跳转结果
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

}
