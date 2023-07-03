package com.seektop.fund.payment.hongzuanshipay;

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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.HONGBAOSHIPAY + "")
public class HongzuanshiPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            String payType;
            if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                payType = "ali";
            } else {
                payType = "wei";
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String paytype) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("appKey", account.getMerchantCode());
        params.put("outOrderId", req.getOrderId());
        params.put("orderFund", req.getAmount().toString());
        params.put("callbackUrl", account.getNotifyUrl() + merchant.getId());
        params.put("payType", paytype);
        String str = String.format("appKey=%soutOrderId=%sorderFund=%scallbackUrl=%skey=%s", params.get("appKey"), params.get("outOrderId"), params.get("orderFund"), params.get("callbackUrl"), account.getPrivateKey());
        params.put("sign", MD5.md5(str));
        log.info("HongzuanshiPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HONGBAOSHI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HONGBAOSHI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/open/match_order/add", params, requestHeader);
        log.info("HongzuanshiPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if (!"0".equals(json.getString("code"))) {
            throw new RuntimeException("创建订单失败");
        }
        String link = json.getJSONObject("data").getString("pcUrl");
        result.setRedirectUrl(link);
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("HongzuanshiPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("outOrderId");
        String sign = resMap.remove("sign");
        String str = String.format("appKey=%soutOrderId=%sorderFund=%sorderId=%srealOrderFund=%skey=%s", account.getMerchantCode(), resMap.get("outOrderId"),
                resMap.get("orderFund"), resMap.get("orderId"), resMap.get("realOrderFund"), account.getPrivateKey());
        if (null != orderId && !"".equals(orderId) && sign.equals(MD5.md5(str))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(new BigDecimal(resMap.get("realOrderFund")).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(resMap.get("orderId"));
            return pay;
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return null;
    }
}
