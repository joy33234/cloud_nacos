package com.seektop.fund.payment.stpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
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
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.STPAYER + "")
public class STPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("user_id", req.getUserId().toString());
        params.put("username", req.getUsername());
        params.put("amount", req.getAmount().toString());
        params.put("order_id", req.getOrderId());
        params.put("pay_bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
        params.put("pay_user", req.getKeyword().split("\\|\\|")[0]);
        params.put("remark", req.getKeyword().split("\\|\\|")[1]);
        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()));
            if (level != null) {
                params.put("card_group", level.getName());
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "");
        String key = account.getPrivateKey();
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8");
        log.info("STPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        String resStr = this.getPostResult(account.getPayUrl() + "/api/recharge/submit", sign, params, account.getMerchantCode());
        log.info("STPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr) || 1 != JSONObject.parseObject(resStr).getInteger("code")) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject resp = JSONObject.parseObject(resStr);
        JSONObject data = resp.getJSONObject("data");
        if (data != null) {
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), data.getString("card_bank"));
            BankInfo bankInfo = new BankInfo();
            bankInfo.setName(data.getString("card_owner"));
            bankInfo.setBankId(bank.getBankId());
            bankInfo.setBankName(bank.getBankName());
            bankInfo.setBankBranchName(data.getString("card_branch"));
            bankInfo.setCardNo(data.getString("card_num"));
            bankInfo.setKeyword(req.getKeyword().split("\\|\\|")[1]);
            result.setBankInfo(bankInfo);
        }
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("STPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("order_id");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        String key = account.getPrivateKey();
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8");
        log.info("STPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = this.getPostResult(account.getPayUrl() + "/api/recharge/status", sign, params, account.getMerchantCode());
        log.info("STPayer_query_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr) || 1 != JSONObject.parseObject(resStr).getInteger("code")) {
            return null;
        }
        JSONObject resp = JSONObject.parseObject(resStr);
        JSONObject data = resp.getJSONObject("data");
        if ("20".equals(data.getString("status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setOrderId(data.getString("order_id"));
            pay.setAmount(data.getBigDecimal("pay_amount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setThirdOrderId("");

            //针对ST补单，调整实际收款账户信息
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(account.getChannelId(), data.getString("to_bank"));
            if (null != bank) {
                pay.setBankId(bank.getBankId());
                pay.setBankName(bank.getBankName());
                pay.setBankCardName(data.getString("to_card_owner"));
                pay.setBankCardNo(data.getString("to_card_num"));
            }
            return pay;
        }
        return null;
    }

    public void cancel(GlPaymentMerchantaccount payment, GlRecharge req) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", req.getOrderId());
        String key = payment.getPrivateKey();
        String sign = rsaSign(JSONObject.toJSONString(params), key, "UTF-8");
        log.info("STPayer_cancel_params:{}", JSON.toJSONString(params));
        String resp = this.getPostResult(payment.getPayUrl() + "/api/recharge/cancel", sign, params, payment.getMerchantCode());
        log.info("STPayer_cancel_resp:{}", resp);
    }




    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("user_id", req.getUserId().toString());
        params.put("username", req.getUsername());
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        params.put("order_id", req.getOrderId());
        params.put("to_card_bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(),account.getChannelId()));
        params.put("to_card_owner", req.getName());
        params.put("to_card_num", req.getCardNo());
        if (StringUtils.isNotEmpty(req.getUserLevel())) {
            GlFundUserlevel level = glFundUserlevelBusiness.findById(Integer.valueOf(req.getUserLevel()));
            if (level != null) {
                params.put("card_group", level.getName());
            }
        }
        params.put("timestamp", System.currentTimeMillis() + "");

        String sig = rsaSign(JSONObject.toJSONString(params), account.getPrivateKey(), "UTF-8");
        log.info("======STPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        String resStr = this.getPostResult(account.getPayUrl() + "/api/withdraw/submit", sig, params, account.getMerchantCode());
        log.info("=========STPayer_Transfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject resp = JSONObject.parseObject(resStr);
        JSONObject json = resp.getJSONObject("data");
        if (json == null || 1 != resp.getInteger("code")){
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }


    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("========STPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("order_id");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("order_id", orderId);

        String sig = rsaSign(JSONObject.toJSONString(params), merchant.getPrivateKey(), "UTF-8");
        log.info("======STPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = this.getPostResult(merchant.getPayUrl() + "/api/withdraw/status", sig, params, merchant.getMerchantCode());
        log.info("===========STPayer_TransferQuery_resStr:{}", resStr);

        JSONObject resp = JSONObject.parseObject(resStr);
        JSONObject json = resp.getJSONObject("data");
        if (json == null) {
            return null;
        }

        WithdrawNotify notify = new WithdrawNotify();
        if (1 == JSONObject.parseObject(resStr).getInteger("code")) {
            notify.setAmount(json.getBigDecimal("amount"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(json.getString("order_id"));
            notify.setThirdOrderId("");
            if (json.getString("status").equals("15")) {//订单状态判断标准：10:待分单  11:待执行 12:执行中 13:回执验证  14:闲置挂起 15:成功 16:失败 17:已撤销
                notify.setStatus(0);
                notify.setRemark("SUCCESS");
                notify.setOutCardName(json.getString("from_user"));
                notify.setOutCardNo(json.getString("from_card_num"));
                GlPaymentChannelBank channelBank = glPaymentChannelBankBusiness.getChannelBank(merchant.getChannelId(), json.getString("from_bank"));
                if (null != channelBank) {
                    notify.setOutBankFlag(channelBank.getBankName());
                }
            } else if (json.getString("status").equals("16") || json.getString("status").equals("17")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return BigDecimal.ZERO;
    }



    private String getPostResult(String url, String sign, Map<String, String> map, String merchantCode) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("X-ST-ID", merchantCode);
            httpPost.addHeader("X-ST-SIG", sign);
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("Authorization", "Basic c2Vla3RvcDpzdDIwMTk=");
            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(map), "UTF-8"));
            CloseableHttpResponse response = httpclient.execute(httpPost);
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 获取私钥
    private static PrivateKey getPrivateKeyFromPKCS8(byte[] data) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] encodedKey = Base64.decodeBase64(data);
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
    }

    // 生成签名
    public static String rsaSign(String content, String privateKey, String charset) {
        try {
            PrivateKey priKey = getPrivateKeyFromPKCS8(privateKey.getBytes());
            java.security.Signature signature = java.security.Signature.getInstance("SHA1WithRSA");
            signature.initSign(priKey);
            signature.update(content.getBytes(charset));
            byte[] signed = signature.sign();
            return new String(Base64.encodeBase64(signed));
        } catch (InvalidKeySpecException ie) {
            log.warn("RSA私钥格式不正确", ie);
        } catch (Exception e) {
            log.warn("RSA签名失败:", e);
        }
        return "";
    }
}
