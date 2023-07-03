package com.seektop.fund.payment.stormpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;

/**
 * 风云聚合支付
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.STORMPAY + "")
public class StormPay implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler, GlRechargeCancelHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelService;

    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        if (FundConstant.PaymentType.BANKCARD_TRANSFER != merchant.getPaymentId()
                && FundConstant.PaymentType.ALI_TRANSFER != merchant.getPaymentId()) {
            prepareJump(merchant, payment, req, result);
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("cid", payment.getMerchantCode());
            data.put("uid", req.getUsername());
            data.put("time", req.getCreateDate().getTime() / 1000);
            data.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN));
            data.put("order_id", req.getOrderId());
            data.put("category", "remit");

            //分组标识
            if (StringUtils.isNotEmpty(req.getUserLevel())) {
                GlFundUserlevel level = glFundUserlevelService.findById(Integer.valueOf(req.getUserLevel()));
                if (level != null) {
                    data.put("gflag", level.getName());
                }
//                }
            }
            if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                //支付宝转银行卡
                data.put("from_bank_flag", "ALIPAY");
            } else {
                String bankCode = paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId());
                // 银行卡转账
                data.put("from_bank_flag", bankCode);
            }

            data.put("from_username", req.getKeyword().split("\\|\\|")[0]);
            data.put("comment", req.getKeyword().split("\\|\\|")[1]);

            String key = payment.getPrivateKey();
            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);
            log.info("StormPay_Prepare_data:{}", data.toJSONString());

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/place_order");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("StormPay_Response_resStr:{}", resStr);

            JSONObject resObj = JSON.parseObject(resStr);
            if (resObj.getBoolean("success") && resObj.getJSONObject("data") != null) {
                JSONObject dataObj = resObj.getJSONObject("data");
                JSONObject cardObj = dataObj.getJSONObject("card");
                String bankflag = cardObj.getString("bankflag");
                GlPaymentChannelBank bank = paymentChannelBankBusiness.getChannelBank(payment.getChannelId(), bankflag);

                BankInfo bankInfo = new BankInfo();
                bankInfo.setName(cardObj.getString("cardname"));
                if (null != bank) {
                    bankInfo.setBankId(bank.getBankId());
                    bankInfo.setBankName(bank.getBankName());
                }

                bankInfo.setBankBranchName(cardObj.getString("location"));
                bankInfo.setCardNo(cardObj.getString("cardnumber"));
                bankInfo.setKeyword(req.getKeyword().split("\\|\\|")[1]);
                result.setBankInfo(bankInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("StormPay_Prepare_error", e);
        }
    }

    public void prepareJump(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            String bankCode = paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId());
            JSONObject data = new JSONObject();
            data.put("cid", payment.getMerchantCode());
            data.put("uid", req.getUsername());
            data.put("time", req.getCreateDate().getTime() / 1000);
            data.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN));
            data.put("order_id", req.getOrderId());
            data.put("ip", req.getIp());

            StringBuilder toSign = new StringBuilder();
            toSign.append("cid=").append(data.getString("cid"));
            toSign.append("&uid=").append(data.getString("uid"));
            toSign.append("&time=").append(data.getLong("time"));
            toSign.append("&amount=").append(data.getBigDecimal("amount"));
            toSign.append("&order_id=").append(data.getString("order_id"));
            toSign.append("&ip=").append(data.getString("ip"));

            String key = payment.getPrivateKey();
            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(toSign.toString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);
            data.put("sign", sign);

            if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
                data.put("type", "online");
                data.put("tflag", bankCode);
            } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                data.put("type", "qrcode");
                data.put("tflag", "WebMM");
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                data.put("type", "qrcode");
                data.put("tflag", "ALIPAY");
            } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                    || FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
                data.put("type", "qrcode");
                data.put("tflag", "UNIPAY");
            }

            data.put("syncurl", payment.getResultUrl() + merchant.getId());

            log.info("StormPay_Prepare_Jump_data: {}", data.toJSONString());
            result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/dsdf/customer_pay/init_din", data));
        } catch (Exception e) {
            e.printStackTrace();
            log.error("StormPay_Prepare_Error", e);
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
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        log.info("StormPay_Notify_resMap_reqBody: {}", reqBody);
        JSONObject jsonObject = JSON.parseObject(reqBody);
        String orderId = jsonObject.getString("order_id");
        String status = jsonObject.getString("status");
        if (StringUtils.isEmpty(orderId) || StringUtils.isEmpty(status) || !status.equals("verified")) {
            return null;
        }
        return query(payment, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        try {
            log.info("Storm_Query_OrderId:{}", orderId);
            JSONObject data = new JSONObject();
            data.put("cid", payment.getMerchantCode());
            data.put("order_id", orderId);
            data.put("time", System.currentTimeMillis() / 1000);

            String key = payment.getPrivateKey();
            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/query_order");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("StormPay_Query_OrderId: {},Result:{}", orderId, resStr);

            JSONObject resObj = JSON.parseObject(resStr);
            if (resObj.getBoolean("success") && resObj.getJSONObject("order") != null) {
                JSONObject orderObj = resObj.getJSONObject("order");
                if (!"verified".equals(orderObj.getString("status"))) {
                    return null;
                }
                RechargeNotify pay = new RechargeNotify();
                pay.setOrderId(orderObj.getString("order_id"));
                pay.setAmount(orderObj.getBigDecimal("amount"));
                pay.setFee(BigDecimal.ZERO);
                pay.setThirdOrderId(orderObj.getString("mer_order_id"));
                return pay;
            }
        } catch (Exception e) {
            log.error("StormPay_Query_Error", e);
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


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        WithdrawResult result = new WithdrawResult();
        try {
            JSONObject data = new JSONObject();
            data.put("cid", merchantAccount.getMerchantCode());
            data.put("uid", req.getUsername());
            data.put("time", req.getCreateDate().getTime() / 1000);
            data.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN));
            data.put("order_id", req.getOrderId().substring(2, req.getOrderId().length()));
            data.put("to_bank_flag", paymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
            data.put("to_cardnumber", req.getCardNo());
            data.put("to_username", req.getName());
            data.put("location", "河南省,郑州");
            data.put("city", "11-13");
//            data.put("gflag", "darren");
//            data.put("atfs_flag", "darren");
            //分组标识
//            if (StringUtils.isNotEmpty(req.getUserLevel())) {
//                data.put("gflag", FundConstant.WITHDRAW_TAG + req.getUserLevel());
//            }

            log.info("Storm_Transfer_data:{},{}", req.getOrderId(), data.toJSONString());

            String key = merchantAccount.getPrivateKey();

            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchantAccount.getPayUrl() + "/dsdf/api/outer_withdraw");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("Storm_Transfer_response: {},{}", req.getOrderId(), resStr);

            result.setOrderId(req.getOrderId());
            result.setReqData(data.toJSONString());
            result.setResData(resStr);

            if (StringUtils.isEmpty(resStr)) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }
            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !json.getBoolean("success")) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }
            result.setValid(true);
            result.setMessage("Withdraw Pending");
            return result;
        } catch (Exception e) {
            log.error("stormPay_transfer_error:", e);
            result.setValid(false);
            result.setMessage("系统出错：" + e.getMessage());
        }
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        log.info("StormPay_TransferNotify_resMap_reqBody: {}", reqBody);
        JSONObject jsonObject = JSON.parseObject(reqBody);
        String orderId = jsonObject.getString("order_id");

        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        try {
            log.info("StormPay_TransferQuery_OrderId:{}", orderId);
            JSONObject data = new JSONObject();
            data.put("cid", merchant.getMerchantCode());
            data.put("order_id", orderId);
            data.put("time", System.currentTimeMillis() / 1000);
            String key = merchant.getPrivateKey();
            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchant.getPayUrl() + "/dsdf/api/query_withdraw");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            log.info("StormPay_transferQuery_param:{}", JSON.toJSONString(httpPost));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            log.info("StormPay_transferQuery_response:{}", JSON.toJSONString(response2));
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("StormPay_TransferQuery_OrderId: {},Result:{}", orderId, resStr);

            JSONObject resObj = JSON.parseObject(resStr);
            if (resObj.getBoolean("success") && resObj.getJSONObject("order") != null) {
                JSONObject orderObj = resObj.getJSONObject("order");
                WithdrawNotify notify = new WithdrawNotify();
                notify.setAmount(orderObj.getBigDecimal("amount"));
                notify.setMerchantCode(merchant.getMerchantCode());
                notify.setMerchantId(merchant.getMerchantId());
                notify.setMerchantName(merchant.getChannelName());
                notify.setOrderId("TX" + orderId);
                notify.setThirdOrderId(orderId);
                notify.setRemark(orderObj.getString("out_cardnumber"));//风云聚合出款卡号
                if ("verified".equals(orderObj.getString("status"))) {
                    notify.setStatus(0);
                    notify.setRemark("SUCCESS");

                    //风云聚合出款：出款账户信息
                    notify.setOutCardName(orderObj.getString("out_cardname"));
                    notify.setOutCardNo(orderObj.getString("out_cardnumber"));

                    GlPaymentChannelBank channelBank = paymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), orderObj.getString("out_bankflag"));
                    if (null != channelBank) {
                        notify.setOutBankFlag(channelBank.getBankName());
                    }
                } else {
                    notify.setStatus(2);
                }
                notify.setSuccessTime(new Date());
                return notify;
            }
        } catch (Exception e) {
            log.error("StormPay_TransferQuery_Error", e);
        }
        return null;
    }

    @Override
    public void cancel(GlPaymentMerchantaccount payment, GlRecharge req) {
        try {
            JSONObject data = new JSONObject();
            data.put("cid", payment.getMerchantCode());
            data.put("order_id", req.getOrderId());
            data.put("time", System.currentTimeMillis() / 1000);
            String key = payment.getPrivateKey();
            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(payment.getPayUrl() + "/dsdf/api/revoke_order");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("StormPay_Cancel_resStr: {}", resStr);

        } catch (Exception e) {
            log.error("StormPay_Cancel_Error", e);
        }
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        try {
            JSONObject data = new JSONObject();
            data.put("cid", merchantAccount.getMerchantCode());
            data.put("time", System.currentTimeMillis() / 1000);
            String key = merchantAccount.getPrivateKey();

            String HMAC_SHA1_ALGORITHM = "HmacSHA1";
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.toJSONString().getBytes());
            String sign = Base64.encodeBase64String(rawHmac);

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(merchantAccount.getPayUrl() + "/dsdf/api/outer_balance");
            httpPost.addHeader("Content-Hmac", sign);
            httpPost.addHeader("content-type", "application/json");
            httpPost.setEntity(new StringEntity(data.toJSONString(), "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            String resStr = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("StormPay_QueryBalance_resStr: {}", resStr);

            JSONObject resObj = JSON.parseObject(resStr);
            if (resObj.getBoolean("success")) {
                return resObj.getBigDecimal("balance");
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("StormPay_QueryBalance_Error", e);
        }
        return BigDecimal.ZERO;
    }
}
