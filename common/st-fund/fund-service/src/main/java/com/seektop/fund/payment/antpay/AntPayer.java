package com.seektop.fund.payment.antpay;

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
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;


@Slf4j
@Service(FundConstant.PaymentChannel.ANTPAY + "")
public class AntPayer implements GlPaymentRechargeHandler {

    private static final String SERVER_PAY_URL = "/index/index";//支付地址


    private static final String SERVER_QUERY_URL = "/index/index/get_status";//查询订单地址

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("merchantid", account.getMerchantCode());
        params.put("orderid", req.getOrderId());
        params.put("money", req.getAmount() + "");
        params.put("client_ip", "0.0.0.0");
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            params.put("paytype", "alipayscan");//支付宝 动态金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            params.put("paytype", "alipayh5");//支付宝 固定金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            params.put("paytype", "wechat");//微信支付 动态金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY) {
            params.put("paytype", "weixinwap");//微信支付  固定金额
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            params.put("paytype", "tobank");
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            params.put("paytype", "alipaybank");
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY) {
            params.put("paytype", "alipaybank");
        }
        params.put("notify_url", account.getNotifyUrl() + merchant.getId());
        params.put("return_url", account.getResultUrl() + merchant.getId());
        params.put("merchantKey", account.getPrivateKey());
        params.put("sign", MD5.md5(MD5.toAscii(params)));
        params.put("format", "JSON");
        params.remove("merchantKey");
        log.info("AntPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.ANT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANT_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post((account.getPayUrl() + SERVER_PAY_URL), params, requestHeader);
        log.info("AntPayer_recharge_prepare_resp:{}", resStr);
        JSONObject json = this.checkResponse(resStr);
        if (json == null) {
            throw new RuntimeException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("qr_code"));
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("AntPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");
        if (StringUtils.isNotEmpty(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantid", account.getMerchantCode());
        params.put("orderid", orderId);
        params.put("rndstr", RandomStringUtils.randomAlphabetic(10));
        params.put("merchantKey", account.getPrivateKey());
        params.put("sign", MD5.md5(MD5.toAscii(params)));
        params.remove("merchantKey");
        log.info("AntPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ANT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANT_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId.toUpperCase())
                .build();
        String resStr = okHttpUtil.post((account.getPayUrl() + SERVER_QUERY_URL), params, requestHeader);
        log.info("AntPayer_query_resp:{}", resStr);

        JSONObject json = this.checkResponse(resStr);
        if (json == null) {
            return null;
        }
        // 请求成功 2-已付款，1-等待付款
        if ("2".equals(json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("money"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
            return pay;
        }
        return null;
    }


    /**
     * 检验返回数据
     *
     * @param response
     * @return
     */
    private JSONObject checkResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return null;
        }
        JSONObject json = JSON.parseObject(response);
        if (json == null || !"1".equals(json.getString("status"))) {
            return null;
        }
        String data = json.get("data").toString();
        JSONObject dataJson = JSON.parseObject(data);
        return dataJson;
    }
}
