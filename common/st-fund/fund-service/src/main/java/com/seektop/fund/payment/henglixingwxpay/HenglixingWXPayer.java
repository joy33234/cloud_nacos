package com.seektop.fund.payment.henglixingwxpay;

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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.HENGLIXINGWXPAY + "")
public class HenglixingWXPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new HashMap();
        params.put("merchantNo", merchantaccount.getMerchantCode());
        params.put("merchantOrderNo", req.getOrderId());
        params.put("orderAmount", req.getAmount().setScale(0, RoundingMode.DOWN).toString());
        params.put("payWay", "ds_zl_wx_wap");
        params.put("notifyUrl", merchantaccount.getNotifyUrl() + merchant.getId());
        params.put("returnUrl", "");
        params.put("clientIp", req.getIp());
        params.put("timestamp", req.getCreateDate().getTime() + "");
        params.put("sign", ApiSignUtil.buildSignByMd5(params, merchantaccount.getPrivateKey()));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGWX_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGWX_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        try {
            log.info("HenglixingWXPayer_recharge_prepare_params:{}", JSONObject.toJSONString(params));
            String payUrl = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/unifiedCreateOrder", JSON.toJSONString(params), requestHeader);
            log.info("HenglixingWXPayer_recharge_prepare_stage_one_resp:{}", payUrl);
            JSONObject payJson = JSONObject.parseObject(payUrl);
            if (payJson != null && "1".equals(payJson.getString("status"))) {
                Map<String, String> params2 = new HashMap();
                params2.put("tradeId", payJson.getString("data"));
                params2.put("merchantNo", merchantaccount.getMerchantCode());
                params2.put("timestamp", req.getCreateDate().getTime() + "");
                params2.put("sign", ApiSignUtil.buildSignByMd5(params2, merchantaccount.getPrivateKey()));

                log.info("HenglixingWXPayer_recharge_prepare_stage_two_params:{}", JSON.toJSONString(params2));
                payUrl = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/getPayLink", JSON.toJSONString(params2), requestHeader);
                log.info("HenglixingWXPayer_recharge_prepare_stage_two_resp:{}", payUrl);
                payJson = JSONObject.parseObject(payUrl);

                if (payJson != null && payJson.getString("status").equals("1")) {
                    result.setRedirectUrl(payJson.getString("data"));
                }
            }
        } catch (Exception e) {
            throw new GlobalException("创建订单失败");
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, Map<String, String> resMap) throws GlobalException {
        log.info("HenglixingWXPayer_notify_resp:{}", JSON.toJSONString(resMap));
        try {
            String notifyStr = AESEncryptUtil.decrypt(resMap.get("reqBody"), merchantaccount.getPublicKey());
            JSONObject json = JSONObject.parseObject(notifyStr);
            if (json != null && "1".equals(json.getString("status"))) {
                json = json.getJSONObject("data");
                if (null != json && StringUtils.isNotEmpty(json.getString("merchantOrderNo"))) {
                    return this.query(merchantaccount, json.getString("merchantOrderNo"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap();
        params.put("merchantNo", account.getMerchantCode());
        params.put("merchantOrderNo", orderId);
        params.put("timestamp", System.currentTimeMillis() + "");
        params.put("sign", ApiSignUtil.buildSignByMd5(params, account.getPrivateKey()));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HENGLIXINGWX_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGLIXINGWX_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();

        log.info("HenglixingWXPayer_query_params:{}", JSON.toJSONString(params));
        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/api/findOrder", JSON.toJSONString(params), requestHeader);
        log.info("HenglixingWXPayer_query_resp:{}", resp);

        JSONObject json = JSONObject.parseObject(resp);
        if (json != null && "1".equals(json.getString("status"))) {
            json = json.getJSONObject("data");
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("orderAmount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("platformOrderNo"));
            return pay;
        }

        return null;
    }


}
