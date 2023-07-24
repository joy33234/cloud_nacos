package com.seektop.fund.payment.lilypay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
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
@Service(FundConstant.PaymentChannel.LILYPAY + "")
public class LilyPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId() || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            String payType;
            if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
                payType = "ylsm";
            } else {
                if (req.getClientType() == ProjectConstant.ClientType.PC) {
                    payType = "wxsm";
                } else {
                    payType = "wxwap";
                }
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("fxid", account.getMerchantCode());
        params.put("fxddh", req.getOrderId());
        params.put("fxdesc", "utf-8");
        params.put("fxfee", req.getAmount().setScale(0, RoundingMode.DOWN).toString());
        params.put("fxpay", payType);
        params.put("fxnotifyurl", account.getNotifyUrl() + merchant.getId());
        params.put("fxbackurl", account.getNotifyUrl() + merchant.getId());
        params.put("fxnotifystyle", "2");
        params.put("fxip", "0.0.0.0");
        String sign = account.getMerchantCode() + req.getOrderId() + req.getAmount().setScale(0, RoundingMode.DOWN).toString() + account.getNotifyUrl() + merchant.getId() + account.getPrivateKey();
        params.put("fxsign", MD5.md5(sign));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.DEPOSIT.getCode())
                .channelId(PaymentMerchantEnum.LILY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.LILY_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        log.info("LilyPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        String resp;
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            resp = HtmlTemplateUtils.getPost(account.getPayUrl() + "/Pay", params);
        } else {
            resp = okHttpUtil.post(account.getPayUrl() + "/Pay", params, requestHeader);
        }
        log.info("LilyPayer_recharge_prepare_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            throw new RuntimeException("创建订单失败");
        }
        result.setMessage(resp);
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("LilyPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject reqBody = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = reqBody.getString("fxddh");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("fxid", account.getMerchantCode());
        params.put("fxddh", orderId);
        params.put("fxaction", "orderquery");
        String sign = account.getMerchantCode() + orderId + "orderquery" + account.getPrivateKey();
        params.put("fxsign", MD5.md5(sign));
        log.info("LilyPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.DEPOSIT_QUERY.getCode())
                .channelId(PaymentMerchantEnum.LILY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.LILY_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/Pay", params, requestHeader);
        log.info("LilyPayer_query_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resp);
        if ("1".equals(json.getString("fxstatus"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("fxfee").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("fxorder"));
            return pay;
        }
        return null;
    }
}
