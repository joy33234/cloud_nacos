package com.seektop.fund.payment.ruifupay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 睿付支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.RUIFUPAY + "")
public class RuifuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            String payType;
            if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
                payType = "100501";
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                payType = "100202";
            } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
                payType = "100401";
            } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
                payType = "100402";
            } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                payType = "100404";
            } else {
                payType = "100102";
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("mer_no", account.getMerchantCode());
        params.put("mer_order_no", req.getOrderId());
        params.put("order_amount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("busi_code", payType);
        params.put("goods", "Recharge");
        params.put("bg_url", account.getNotifyUrl() + merchant.getId());
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            params.put("realName", req.getFromCardUserName());
        }
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("RuifuPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        String resp;


        if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            resp = HtmlTemplateUtils.getPost(account.getPayUrl() + "/payGateway/payFor", params);
            log.info("RuifuPayer_recharge_prepare_resp:{}", resp);
            result.setMessage(resp);
        } else {
            String payUrl = account.getPayUrl();
            if (payUrl.contains("https")) {
                payUrl = payUrl.replace("https", "http");
            }
            resp = this.doPost(payUrl + "/paid/payOrd", JSON.toJSONString(params), "UTF-8");
            log.info("RuifuPayer_recharge_prepare_resp:{}", resp);
            JSONObject json = JSONObject.parseObject(resp);
            if (!"SUCCESS".equals(json.getString("status"))) {
                throw new RuntimeException("创建订单失败");
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] b = decoder.decode(json.getString("code_url"));
            String url = new String(b, StandardCharsets.UTF_8);
            if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                    || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                result.setRedirectUrl(url);
            } else {
                result.setMessage(HtmlTemplateUtils.getQRCode(url));
            }
        }
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("RuifuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("mer_order_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("mer_no", account.getMerchantCode());
        params.put("mer_order_no", orderId);
        params.put("request_no", System.currentTimeMillis() + "");
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("RuifuPayer_query_params:{}", JSON.toJSONString(params));
        String payUrl = account.getPayUrl();
        if (payUrl.contains("https")) {
            payUrl = payUrl.replace("https", "http");
        }
        String resp = this.doPost(payUrl + "/paid/queryOrd", JSON.toJSONString(params), "UTF-8");
        log.info("RuifuPayer_query_resp:{}", resp);
        JSONObject josn = JSONObject.parseObject(resp);
        if ("SUCCESS".equals(josn.getString("query_status")) && "SUCCESS".equals(josn.getString("order_status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(josn.getBigDecimal("pay_amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(josn.getString("order_no"));
            return pay;
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("mer_no", merchantAccount.getMerchantCode());
        params.put("mer_order_no", req.getOrderId());
        params.put("acc_type", "1");
        params.put("acc_no", req.getCardNo());
        params.put("acc_name", req.getName());
        params.put("order_amount", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("province", "上海市");
        params.put("city", "上海市");
        params.put("bg_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("RuifuPayer_doTransfer_params:{}", JSON.toJSONString(params));
        String resp = this.doPost(merchantAccount.getPayUrl() + "/remit/transOrd", JSON.toJSONString(params), "UTF-8");
        log.info("RuifuPayer_doTransfer_resp:{}", resp);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resp);
        if (StringUtils.isEmpty(resp)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }


        JSONObject json = JSONObject.parseObject(resp);
        if (!"SUCCESS".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json.getString("err_msg"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("err_msg"));
        return result;

    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("RuifuPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("mer_order_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("mer_no", merchant.getMerchantCode());
        params.put("mer_order_no", orderId);
        params.put("request_no", System.currentTimeMillis() + "");
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("RuifuPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        String resp = this.doPost(merchant.getPayUrl() + "/remit/queryOrd", JSON.toJSONString(params), "UTF-8");
        log.info("RuifuPayer_doTransferQuery_resp:{}", resp);
        JSONObject json = JSONObject.parseObject(resp);
        if (!"SUCCESS".equals(json.getString("query_status"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("order_amount").divide(new BigDecimal(100)));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(json.getString("mer_order_no"));
        notify.setThirdOrderId(json.getString("order_no"));
        if (json.getString("status").equals("SUCCESS")) {
            notify.setStatus(0);
        } else if (json.getString("status").equals("FAIL")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("mer_no", merchantAccount.getMerchantCode());
        params.put("request_no", System.currentTimeMillis() + "");
        params.put("request_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("RuifuPayer_queryBalance_params:{}", JSON.toJSONString(params));
        String resp = this.doPost(merchantAccount.getPayUrl() + "/remit/queryBala", JSON.toJSONString(params), "UTF-8");
        log.info("RuifuPayer_queryBalance_resp:{}", resp);
        JSONObject json = JSONObject.parseObject(resp);
        if (!json.getBoolean("status")) {
            return BigDecimal.ZERO;
        }
        return json.getBigDecimal("balance").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
    }

    private static String doPost(String url, String param, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        try {
            if (param != null) {
                httpPost.setEntity(new StringEntity(param, charset));
            }
            httpPost.setHeader("Content-type", "application/json");
            HttpResponse response = httpClient.execute(httpPost);

            log.info(response.toString());
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity, charset);
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
            }
        }
        return null;
    }
}
