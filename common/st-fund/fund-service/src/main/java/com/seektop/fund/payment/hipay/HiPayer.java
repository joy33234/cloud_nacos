
package com.seektop.fund.payment.hipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Hipay 接口
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HIPAY + "")
public class HiPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            params.put("out_trade_no", req.getOrderId());
            params.put("notify_url", merchantaccount.getNotifyUrl() + merchant.getId());
            params.put("paid_name", req.getFromCardUserName());

            Map<String, String> headParams = new HashMap<String, String>();
            headParams.put("Authorization", "Bearer " + merchantaccount.getPrivateKey());
            headParams.put("Accept", "application/json");
            headParams.put("content-type", "application/json");

            log.info("HiPayer_Prepare_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String resStr = okHttpUtil.post(merchantaccount.getPayUrl() + "/api/transaction", params, requestHeader, headParams);
            log.info("HiPayer_Prepare_resStr:{}", resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null) {
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(json.getString("uri"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("HiPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String thirdOrderId = json.getString("trade_no");
        String orderId = json.getString("out_trade_no");
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        log.info("HiPayer_query_params_orderId:{}_thirdOrderId:{}", orderId);

        Map<String, String> headParams = new HashMap<String, String>();
        headParams.put("Authorization", "Bearer " + account.getPrivateKey());
        headParams.put("Accept", "application/json");

        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());

        String resStr = okHttpUtil.get(account.getPayUrl() + "/api/transaction/" + orderId, null, requestHeader, headParams);
        log.info("HiPayer_query_resStr:{}", resStr);


        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        // 订单状态判断标准:  success => 成功  progress => 进行中 timeout => 逾时
        if (json.getString("status").equals("success")) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("trade_no"));
            return pay;
        }
        return null;
    }


    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return null;
    }


    private String getResponse(String contentType, String url, String privatekey, Map<String, String> params) {
        String result = "";
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Authorization", "Bearer " + privatekey);
            httpPost.addHeader("content-type", contentType);
            httpPost.addHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(JSON.toJSONString(params), "UTF-8"));
            CloseableHttpResponse response = httpclient.execute(httpPost);
            result = EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static String doGet(String url, String privatekey) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Authorization", "Bearer " + privatekey);
        httpGet.addHeader("Accept", "application/json");
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine).append("\n");
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
            }
        }
        return null;
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.HI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
