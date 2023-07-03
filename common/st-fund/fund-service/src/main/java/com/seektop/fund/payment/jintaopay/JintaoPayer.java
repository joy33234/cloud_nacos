package com.seektop.fund.payment.jintaopay;

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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.JINTAOPAY + "")
public class JintaoPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            prepareScan(merchant, account, req, result);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> params = new HashMap<>();
        String[] data = account.getMerchantCode().split("\\|\\|");
        params.put("merchantCode", data[0]);
        params.put("payProductNo", "7301462765021");
        params.put("orderNo", req.getOrderId());
        params.put("orderAmount", req.getAmount().setScale(0, RoundingMode.DOWN).toString());
        params.put("interfaceVersion", "V1.0");
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        params.put("returnUrl", account.getResultUrl() + merchant.getId());
        params.put("ReturnParam", "OrderNo");
        params.put("orderTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("productName", "Recharge");
        params.put("productCode", "Recharge");
        params.put("productNum", "Recharge");
        params.put("productDesc", "Recharge");
        try {
            String rsaString = MD5.md5(buildRSAString(params, data[1]));
            String sign = RSACryptoUtil.encryptByPublicKey(rsaString, account.getPublicKey(), "UTF-8");
            params.put("sign", sign);
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("JintaoPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JINTAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINTAO_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/OrderOp/GetRecOrderLink", params, requestHeader);
        log.info("JintaoPayer_recharge_prepare_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resp);
        result.setRedirectUrl(json.getString("linkUrl"));
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JintaoPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        String[] data = account.getMerchantCode().split("\\|\\|");
        params.put("merchantCode", data[0]);
        params.put("orderNo", orderId);
        String sign = "orderNo=" + orderId + "&merchantCode=" + data[0] + "&key=" + data[1];
        try {
            params.put("sign", RSACryptoUtil.encryptByPublicKey(MD5.md5(sign), account.getPublicKey(), "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("JintaoPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JINTAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINTAO_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resp = okHttpUtil.get(account.getPayUrl() + "/OrderOp/Query", params, requestHeader);
        log.info("JintaoPayer_query_resp:{}", resp);
        JSONObject json = JSONObject.parseObject(resp);
        if ("0".equals(json.getString("result")) && "5".equals(json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("OrderAmount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
            return pay;
        }
        return null;
    }


    private String buildRSAString(Map<String, String> map, String key) {
        String merchantCode = map.get("merchantCode");
        String notifyUrl = map.get("notifyUrl");
        String interfaceVersion = map.get("interfaceVersion");
        String returnUrl = map.get("returnUrl");
        String orderNo = map.get("orderNo");
        String orderTime = map.get("orderTime");
        String orderAmount = map.get("orderAmount");
        String productName = map.get("productName");
        String productCode = map.get("productCode");
        String productNum = map.get("productNum");
        String productDesc = map.get("productDesc");
        String ReturnParam = map.get("ReturnParam");
        String payProductNo = map.get("payProductNo");
        StringBuilder buff = new StringBuilder();
        if (StringUtils.isNotEmpty(merchantCode)) {
            buff.append("merchantCode=").append(merchantCode).append("&");
        }
        if (StringUtils.isNotEmpty(notifyUrl)) {
            buff.append("notifyUrl=").append(notifyUrl).append("&");
        }
        if (StringUtils.isNotEmpty(interfaceVersion)) {
            buff.append("interfaceVersion=").append(interfaceVersion).append("&");
        }
        if (StringUtils.isNotEmpty(returnUrl)) {
            buff.append("returnUrl=").append(returnUrl).append("&");
        }
        if (StringUtils.isNotEmpty(orderNo)) {
            buff.append("orderNo=").append(orderNo).append("&");
        }
        if (StringUtils.isNotEmpty(orderTime)) {
            buff.append("orderTime=").append(orderTime).append("&");
        }
        if (StringUtils.isNotEmpty(orderAmount)) {
            buff.append("orderAmount=").append(orderAmount).append("&");
        }
        if (StringUtils.isNotEmpty(productName)) {
            buff.append("productName=").append(productName).append("&");
        }
        if (StringUtils.isNotEmpty(productCode)) {
            buff.append("productCode=").append(productCode).append("&");
        }
        if (StringUtils.isNotEmpty(productNum)) {
            buff.append("productNum=").append(productNum).append("&");
        }
        if (StringUtils.isNotEmpty(productDesc)) {
            buff.append("productDesc=").append(productDesc).append("&");
        }
        if (StringUtils.isNotEmpty(ReturnParam)) {
            buff.append("ReturnParam=").append(ReturnParam).append("&");
        }
        if (StringUtils.isNotEmpty(payProductNo)) {
            buff.append("payProductNo=").append(payProductNo).append("&");
        }
        buff.append("key=").append(key);
        return buff.toString();
    }
}
