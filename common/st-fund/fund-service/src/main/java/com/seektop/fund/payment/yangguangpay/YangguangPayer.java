package com.seektop.fund.payment.yangguangpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * yangguang支付 by osiris
 */
@Slf4j
@Service(FundConstant.PaymentChannel.YANGGUANG + "")
public class YangguangPayer implements GlPaymentRechargeHandler {

    /*
    微信，支付宝，云闪付，快捷，网关
     */

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    //充值提交
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req,
                        GlRechargeResult result) {
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY ||
                merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY ||
                merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            prepareToUnified(merchant, account, req, result);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY ||
                merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY) {
            prepareToGateway(merchant, account, req, result);
        }
    }

    public void prepareToUnified(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req,
                                 GlRechargeResult result) {
        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = account.getPrivateKey();// 私钥

        param.put("orderNumber", req.getOrderId());
        param.put("version", "V2.0.0");
        param.put("merchantNumber", account.getMerchantCode());
        param.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).toString());
        param.put("currency", "CNY");
        param.put("commodityName", "CZ");
        param.put("commodityDesc", "CZ");
        String payType = "ALI_H5";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            payType = "ALI_H5";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            payType = "WECHAT_NATIVE";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                payType = "WECHAT_H5";
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            payType = "UNION_H5";
        }
        param.put("payType", payType);
        param.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        param.put("orderCreateIp", req.getIp());
        param.put("key", keyValue);
        String toSign = MD5.toAscii(param);
        param.put("sign", MD5.md5(toSign));
        param.remove("key");

        log.info("Yangguang_recharge_prepare_params:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.SUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SUN_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();

        String url = account.getPayUrl() + "/api/open/unifiedPay";
        String retBack = okHttpUtil.postJSON(url, JSON.toJSONString(param), requestHeader);
        log.info("Yangguang_recharge_prepare_result:{}", retBack);
        JSONObject jsonData = JSONObject.parseObject(retBack);
        if (jsonData != null && jsonData.getBoolean("success")) {
            JSONObject context = jsonData.getJSONObject("context");
            if (context != null && context.getString("merchantOrderNumber").equals(req.getOrderId())) {
                result.setRedirectUrl(context.getString("payurl"));
            }
        } else {
            throw new RuntimeException("创建订单失败");
        }
    }

    public void prepareToGateway(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req,
                                 GlRechargeResult result) {
        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = account.getPrivateKey();// 私钥

        param.put("merchantNumber", account.getMerchantCode());
        param.put("version", "V2.0.0");
        param.put("orderNumber", req.getOrderId());
        param.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).toString());
        param.put("currency", "CNY");
        param.put("commodityName", "CZ");
        param.put("commodityDesc", "CZ");

        String payType = "B2C";
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            payType = "B2C";
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            payType = "QUICK";
        }
        param.put("payType", payType);

        param.put("cardType", "SAVINGS");
        param.put("orderCreateIp", req.getIp());
        param.put("bankNumber", "1000");
        param.put("returnUrl", account.getResultUrl() + merchant.getId());
        param.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        param.put("userId", req.getUsername());

        param.put("key", keyValue);
        String toSign = MD5.toAscii(param);
        param.put("sign", MD5.md5(toSign));

        param.remove("key");
        //网关支付无返回结果
        log.info("Yangguang_recharge_prepare_params:{}", JSON.toJSONString(param));
        String url = account.getPayUrl() + "/api/open/gatewayPay？" + MD5.toAscii(param);
        result.setRedirectUrl(url);
    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment,
                                 Map<String, String> resMap) {

        log.info("Yangguangpayer_Notify_resMap:{}", resMap);

        String resStr = resMap.get("reqBody");
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        String orderStatus = json.getString("orderStatus");
        String orderId = json.getString("merchantOrderNumber");
        if (StringUtils.isEmpty(orderStatus) || !"SUC".equals(orderStatus) || StringUtils.isEmpty(orderId)) {
            return null;
        }

        return query(payment, orderId);
    }


    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) {
        String keyValue = account.getPrivateKey();// 私钥
        Map<String, String> param = new HashMap<>();

        param.put("merchantNumber", account.getMerchantCode());
        param.put("orderNumber", orderId);
        param.put("key", keyValue);
        String toSign = MD5.toAscii(param);
        param.put("sign", MD5.md5(toSign));
        param.remove("key");
        log.info("YangguangPayer_Query_reqMap:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String url = account.getPayUrl() + "/api/open/query/queryTradeOrder";
        String retBack = okHttpUtil.postJSON(url, JSON.toJSONString(param), requestHeader);
        log.info("YangguangPayer_Query_resStr:{}", retBack);
        if (StringUtils.isEmpty(retBack)) {
            return null;
        }

        JSONObject retJson = JSONObject.parseObject(retBack);
        if (null == retJson || !retJson.getBoolean("success")) {
            return null;
        }
        JSONObject context = retJson.getJSONObject("context");
        if (null == context) {
            return null;
        }
        if (StringUtils.isNotEmpty(context.getString("orderStatus")) && "SUC".equals(context.getString("orderStatus"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(context.getBigDecimal("amount").divide(BigDecimal.valueOf(100)));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(context.getString("merchantOrderNumber"));
            pay.setThirdOrderId(context.getString("orderNumber"));
            return pay;
        }
        return null;
    }
}
