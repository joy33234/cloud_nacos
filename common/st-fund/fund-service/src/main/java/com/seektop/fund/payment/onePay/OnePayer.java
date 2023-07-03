package com.seektop.fund.payment.onePay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.common.utils.RSASignature;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * onePay支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.ONEPAY + "")
public class OnePayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> param = new HashMap<>();
        param.put("version", "1.0");
        param.put("inputCharset", "UTF-8");
        param.put("returnUrl", account.getResultUrl() + merchant.getId());
        param.put("notifyUrl", account.getNotifyUrl() + merchant.getId());

        param.put("deviceType", "WEB");
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()
                && ProjectConstant.ClientType.PC == req.getClientType()) {
            //网银支付
            param.put("payType", "EC");
            param.put("subIssuingBank", paymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            //快捷支付
            if (ProjectConstant.ClientType.PC != req.getClientType()) {
                param.put("deviceType", "H5");
            }
            param.put("payType", "NC");
            param.put("cardType", "D");
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            // 银行卡转账
            param.put("payType", "CARDBANK");
            param.put("cardType", "D");
            param.put("paymentCard", req.getFromCardNo());
            param.put("userName", req.getFromCardUserName());
        }

        param.put("merchantId", account.getMerchantCode());
        param.put("merchantTradeId", req.getOrderId());
        param.put("currency", "CNY");
        param.put("amountFee", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        param.put("goodsTitle", "CZ");
        param.put("issuingBank", "UNIONPAY");

        String toSign = MD5.toAscii(param);
        String sign = RSASignature.sign(toSign, account.getPrivateKey());

        param.put("sign", SignUtils.str2HexStr(sign));
        param.put("signType", "RSA");

        log.info("OnePay_prepare_param: {}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.ONE_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ONE_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/payment/v3/checkOut.html", param, requestHeader);

        log.info("OnePay_prepare_resStr: {}", JSON.toJSONString(resStr));
        result.setMessage(resStr);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("OnePay_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("merchantTradeId");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return query(account, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> param = new HashMap<>();
        param.put("merchantId", account.getMerchantCode());
        param.put("merchantTradeId", orderId);
        String toSign = MD5.toAscii(param);

        String sign = RSASignature.sign(toSign, account.getPrivateKey());
        param.put("sign", SignUtils.str2HexStr(sign));
        param.put("signType", "RSA");

        log.info("OnePay_query_param: " + param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ONE_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ONE_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/query/unionPayOrder.html", param, requestHeader);

        log.info("OnePay_query_resStr: " + resStr);
        if (ObjectUtils.isEmpty(resStr)) {
            return null;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getString("flag") == "FAILED") {
            return null;
        }

        if (null == json.getJSONObject("data") || ObjectUtils.isEmpty(json.getJSONObject("data").getJSONArray("row_detail"))) {
            return null;
        }

        JSONArray rowDetail = json.getJSONObject("data").getJSONArray("row_detail");

        JSONObject datail = (JSONObject) rowDetail.get(0);


        if (!"PS_PAYMENT_SUCCESS".equals(datail.getString("tradeStatus"))) {
            return null;
        }

        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(datail.getBigDecimal("amountFee"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(datail.getString("pwTradeId"));
        return pay;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("merchantId", merchantAccount.getMerchantCode());
            param.put("batchNo", req.getOrderId());
            param.put("batchRecord", "1");
            param.put("currencyCode", "CNY");
            param.put("totalAmount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
            param.put("payDate", DateUtils.getStrCurDate(new Date(), DateUtils.YYYYMMDD));
            param.put("isWithdrawNow", "3");
            param.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

            Map<String, String> signMap = new HashMap<>();
            signMap.put("merchantId", merchantAccount.getMerchantCode());
            signMap.put("batchNo", req.getOrderId());
            signMap.put("batchRecord", "1");
            signMap.put("currencyCode", "CNY");
            signMap.put("totalAmount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
            signMap.put("payDate", DateUtils.getStrCurDate(new Date(), DateUtils.YYYYMMDD));
            signMap.put("isWithdrawNow", "3");
            signMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
            String toSign = MD5.toAscii(signMap);

            String sign = RSASignature.sign(toSign, merchantAccount.getPrivateKey());
            param.put("sign", SignUtils.str2HexStr(sign));

            param.put("signType", "RSA");

            Map<String, String> detail = new HashMap<>();
            detail.put("receiveType", "个人");
            detail.put("accountType", "储蓄卡");
            detail.put("serialNo", req.getOrderId());
            detail.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
            detail.put("purpose", "1001");
            detail.put("bankName", paymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
            detail.put("bankNo", req.getCardNo());
            detail.put("receiveName", req.getName());

            List<Map<String, String>> detailList = new ArrayList<>();
            detailList.add(detail);
            param.put("detailList", detailList);

            log.info("OnePayer_Transfer_param: {}", JSON.toJSONString(param));

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchantAccount.getPayUrl() + "/v2/distribute/withdraw.html");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(JSON.toJSONString(param), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");

            log.info("OnePayer_Transfer_resStr: {}", resStr);
            WithdrawResult result = new WithdrawResult();
            result.setOrderId(req.getOrderId());
            result.setReqData(JSON.toJSONString(param));
            result.setResData(resStr);
            if (StringUtils.isEmpty(resStr)) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }

            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !"SUCCESS".equals(json.getString("flag"))) {
                result.setValid(false);
                result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("errorMsg"));
                return result;
            }
            result.setValid(true);
            result.setReqData(JSON.toJSONString(param));
            result.setResData(resStr);

            return result;
        } catch (Exception e) {
            log.error("OnePayer_doTransfer_Error", e);
            throw new RuntimeException("创建订单失败");
        }
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("OnePay_TransferNotify_resMap:{}", JSON.toJSONString(resMap));
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        JSONObject reqData = JSON.parseObject(reqBody);
        if (reqData == null) {
            return null;
        }
        String dataStr = reqData.getString("data");
        JSONObject dataJson = JSON.parseObject(dataStr);
        log.info("OnePay_TransferNotify_dataJson:{}", JSON.toJSONString(dataJson));
        if (StringUtils.isNotEmpty(dataJson.getString("batchNo"))) {
            return doTransferQuery(merchant, dataJson.getString("batchNo"));
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        try {
            Map<String, String> param = new HashMap<>();
            param.put("merchantId", merchant.getMerchantCode());
            param.put("batchNo", orderId);

            String toSign = MD5.toAscii(param);
            String sign = RSASignature.sign(toSign, merchant.getPrivateKey());
            param.put("sign", SignUtils.str2HexStr(sign));
            param.put("signType", "RSA");

            log.info("OnePayer_TransferQuery_param: {}", JSON.toJSONString(param));

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchant.getPayUrl() + "/v2/distribute/queryWithdraw.html");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(JSON.toJSONString(param), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("OnePayer_TransferQuery_resStr: {}", resStr);
            if (StringUtils.isEmpty(resStr)) {
                return null;
            }
            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !"SUCCESS".equals(json.getString("flag"))) {
                return null;
            }

            JSONObject dataJson = json.getJSONObject("data");
            if (null == dataJson) {
                return null;
            }

            JSONArray jsonArray = dataJson.getJSONArray("detailList");
            if (null == jsonArray) {
                return null;
            }
            JSONObject withdrawData = (JSONObject) jsonArray.get(0);

            WithdrawNotify notify = new WithdrawNotify();
            if ("5".equals(dataJson.getString("status")) && withdrawData.getString("tradeStatus").equals("1")) {
                notify.setStatus(0);
            } else if ("7".equals(dataJson.getString("status")) && withdrawData.getString("tradeStatus").equals("2")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }

            notify.setAmount(withdrawData.getBigDecimal("amount"));

            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setOrderId(withdrawData.getString("serialNo"));
            return notify;
        } catch (Exception e) {
            log.error("OnePayer_doTransferQuery_error", e);
        }
        return null;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        try {
            Map<String, String> param = new HashMap<>();
            param.put("merchantId", merchantAccount.getMerchantCode());
            param.put("currencyCode", "CNY");

            String toSign = MD5.toAscii(param);
            String sign = RSASignature.sign(toSign, merchantAccount.getPrivateKey());

            param.put("sign", SignUtils.str2HexStr(sign));
            param.put("signType", "RSA");

            log.info("OnePayer_queryBalance_param: {}", JSON.toJSONString(param));

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchantAccount.getPayUrl() + "/v2/distribute/queryBalance.html");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(JSON.toJSONString(param), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");

            log.info("OnePayer_queryBalance_resStr: {}", resStr);

            if (StringUtils.isEmpty(resStr)) {
                return BigDecimal.ZERO;
            }

            JSONObject json = JSON.parseObject(resStr);

            if (json == null || !"SUCCESS".equals(json.getString("flag")) || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
                return BigDecimal.ZERO;
            }

            return json.getJSONObject("data").getBigDecimal("accountAmount");
        } catch (Exception e) {
            log.error("OnePayer_queryBalance_Error", e);
            return BigDecimal.ZERO;
        }
    }
}
