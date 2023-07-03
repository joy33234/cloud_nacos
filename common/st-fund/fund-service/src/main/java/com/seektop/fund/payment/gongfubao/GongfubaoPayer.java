package com.seektop.fund.payment.gongfubao;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.GlPaymentRechargeHandler;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.RechargeNotify;
import com.seektop.fund.payment.RechargePrepareDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.GONGFUBAO + "")
public class GongfubaoPayer implements GlPaymentRechargeHandler {

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        try {
            Map<String, String> param = new LinkedHashMap<>();
            String keyValue = account.getPrivateKey();// AccessToken
            param.put("trade_no", req.getOrderId());
            param.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
            param.put("notify_url", account.getNotifyUrl() + merchant.getId());
            param.put("ip", req.getIp());
            //提交无签名校验，但需设置Authorization的值
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(account.getPayUrl() + "/api/transaction");
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.addHeader("Authorization", "Bearer " + keyValue);//Bearer后有空格
            List<NameValuePair> params = new ArrayList<>();
            param.entrySet().forEach(entry ->
                    params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()))
            );
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            CloseableHttpResponse response2 = httpclient.execute(httpPost);
            log.info("GongfubaoPayer_recharge_prepare_params:{}", JSON.toJSONString(param));
            String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("GongfubaoPayer_recharge_Response_resStr:{}", retBack);
            JSONObject retJson = JSONObject.parseObject(retBack);
            if ("200".equals(retJson.getString("code")) && StringUtils.isNotEmpty(retJson.getString("uri"))) {
                String uri = retJson.getString("uri").replaceAll("\\\\", "");
                result.setRedirectUrl(uri);
            } else {
                throw new RuntimeException("gongfubao创建订单失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("gongfubao创建订单失败");
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("GongfubaoPayer_Notify_resMap:{}", resMap);

        JSONObject json = JSON.parseObject(resMap.get("reqBody"), Feature.OrderedField);
        if (null == json) {
            return null;
        }
        String signature = json.getString("signature");
        json.remove("signature");

        String toSign = json.toJSONString() + account.getPrivateKey();
        String sign = MD5.md5(toSign);

        String status = json.getString("status");
        //回调的时候订单状态是英文success
        if (StringUtils.isNotEmpty(status) && "success".equals(status) && signature.equals(sign)) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("receive_amount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(json.getString("trade_no"));
            pay.setThirdOrderId(json.getString("transaction_id"));
            return pay;
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        try {
            String keyValue = account.getPrivateKey();// AccessToken
            //提交无签名校验，但需设置Authorization的值
            CloseableHttpClient httpclient = HttpClients.createDefault();
            String url = account.getPayUrl() + "/api/transaction/" + orderId;
            HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("Content-Type", "application/json");
            httpGet.addHeader("Authorization", "Bearer " + keyValue);//Bearer后有空格
            CloseableHttpResponse response2 = httpclient.execute(httpGet);
            log.info("GongfubaoPayer_recharge_query_url:{}", url);
            String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8");
            log.info("GongfubaoPayer_recharge_query_Response_resStr:{}", retBack);
            JSONObject retJson = JSONObject.parseObject(retBack);
            //主动查询的时候订单状态是中文“成功”
            if ("true".equals(retJson.getString("success")) && "success".equals(retJson.getString("status"))) {
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(retJson.getBigDecimal("receive_amount"));
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId);
                pay.setThirdOrderId(retJson.getString("transaction_no"));
                return pay;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
