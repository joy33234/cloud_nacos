package com.seektop.fund.payment.yizhifupay;

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
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.YIZHIFUPAY + "")
public class YizhifuPayer implements GlPaymentRechargeHandler {


    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("merchantNo", account.getMerchantCode());
        params.put("orderAmount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("orderNo", req.getOrderId());
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        params.put("callbackUrl", account.getResultUrl() + merchant.getId());
        params.put("payType", "4");
        String toSign = MD5.toAscii(params);
        params.put("sign", MD5.md5(toSign + account.getPrivateKey()));
        log.info("YizhifuPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.YIZHIFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIZHIFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/payapi/order", params, requestHeader);
        log.info("YizhifuPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if ("T".equals(json.getString("status"))) {
            result.setRedirectUrl(json.getString("payUrl"));
        } else {
            throw new RuntimeException("创建订单失败");
        }

    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("YizhifuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("merchantNo", account.getMerchantCode());
        params.put("orderNo", orderId);
        String toSign = MD5.toAscii(params);
        params.put("sign", MD5.md5(toSign + account.getPrivateKey()));
        log.info("YizhifuPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.YIZHIFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIZHIFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/payapi/query", params, requestHeader);
        log.info("YizhifuPayer_query_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if ("T".equals(json.getString("status")) && "SUCCESS".equals(json.getString("orderStatus"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("orderAmount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("wtfOrderNo"));
            return pay;
        }
        return null;
    }
}
