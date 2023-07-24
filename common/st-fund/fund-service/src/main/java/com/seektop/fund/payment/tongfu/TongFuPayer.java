package com.seektop.fund.payment.tongfu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
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
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service(FundConstant.PaymentChannel.TONGFU + "")
public class TongFuPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("payAmount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        paramMap.put("commercialOrderNo", req.getOrderId());
        paramMap.put("callBackUrl", account.getNotifyUrl() + merchant.getId());
        paramMap.put("notifyUrl", account.getResultUrl() + merchant.getId());

        String payURL = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            paramMap.put("payType", "2");
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyGetQrCode";
            if (ProjectConstant.ClientType.PC != req.getClientType()) {//PC
                payURL = account.getPayUrl() + "/api/guest/pay/payApplyGetQrCodeH5";
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            paramMap.put("payType", "3");
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyYsf";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                paramMap.put("isMobile", "2");
            } else if (req.getClientType() == ProjectConstant.ClientType.APP) {
                paramMap.put("isMobile", "1");
            }
            payURL = account.getPayUrl() + "/api/guest/tianbao/wxPay";
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            paramMap.put("payType", "2");
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyApi";
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payURL = account.getPayUrl() + "/api/guest/pay/payApplyToBank";
        }
        log.info("TongfuPay_prepare_paramMap:{}", paramMap);
        String json = JsonUtil.toJson(paramMap);
        //MD5得到sign
        String sign = EncryptUtil.md5(json);
        //aes 加密得到 parameter
        String parameter = EncryptUtil.aes(json, account.getPrivateKey());

        Map<String, String> param = new HashMap<>();
        param.put("platformno", account.getMerchantCode());
        param.put("parameter", parameter);
        param.put("sign", sign);

        log.info("TongfuPay_prepare_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.DEPOSIT.getCode())
                .channelId(PaymentMerchantEnum.TONGFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TONGFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();

        String resStr = okHttpUtil.post(payURL, param, requestHeader);
        log.info("TongfuPay_prepare_resStr:{}", resStr);
        JSONObject jsonObj = JSON.parseObject(resStr);

        if (null == jsonObj || !jsonObj.getString("result").equals("success")) {
            throw new RuntimeException("创建订单失败");
        }

        if (StringUtils.isNotEmpty(jsonObj.getString("payUrl")) && !jsonObj.getString("payUrl").equals("null")) {
            result.setRedirectUrl(jsonObj.getString("payUrl"));
        }

        if (StringUtils.isNotEmpty(jsonObj.getString("localtionView")) && !jsonObj.getString("payUrl").equals("localtionView")) {
            result.setRedirectUrl(jsonObj.getString("localtionView"));
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("TongFuPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String parameter = resMap.get("parameter");
        if (StringUtils.isEmpty(parameter)) {
            return null;
        }
        Map<String, String> paramMap = JsonUtil.toBean(DecryptUtil.aes(parameter, account.getPrivateKey()), Map.class);
        if (ObjectUtils.isEmpty(paramMap)) {
            return null;
        }
        if ("faild".equals(paramMap.get("result"))) {
            return null;
        }
        String orderId = paramMap.get("commercialOrderNo");
        return query(account, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("commercialOrderNo", orderId);
        paramMap.put("type", "1");

        String json = JsonUtil.toJson(paramMap);
        //MD5得到sign
        String md5Sign = EncryptUtil.md5(json);
        //aes 加密得到 parameter
        Optional<String> aesSign = Optional.ofNullable(EncryptUtil.aes(json, account.getPrivateKey()));

        Map<String, String> param = new HashMap<>();
        param.put("platformno", account.getMerchantCode());
        param.put("parameter", aesSign.orElse(StringUtils.EMPTY));
        param.put("sign", md5Sign);

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.TONGFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TONGFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        log.info("Tongfu_query_param:{}", JSON.toJSONString(param));
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/guest/pay/commercialInfo", param, requestHeader);

        log.info("Tongfu_query_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }

        JSONObject jsonObj = JSON.parseObject(resStr);
        if (jsonObj == null || "faild".equals(jsonObj.getString("result")) || !"支付成功".equals(jsonObj.getString("status"))) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();

        pay.setAmount(jsonObj.getBigDecimal("orderAmount"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(jsonObj.getString("orderNo"));
        return pay;
    }
}
