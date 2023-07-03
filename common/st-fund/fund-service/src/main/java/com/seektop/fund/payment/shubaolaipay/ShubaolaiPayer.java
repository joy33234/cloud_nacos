package com.seektop.fund.payment.shubaolaipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
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
@Service(FundConstant.PaymentChannel.SHUBAOLAIPAY + "")
public class ShubaolaiPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness channelBankBusiness;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"ALIPAY_QRCODE");
        }else if(FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"WECHAT_QRCODE");
        } else if(FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"BANK_QRCODE");
        } else if(FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"WEB_BANK");
        }else if(FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"ALIPAY_H5_B");
        }else if(FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()){
            prepareToScan(merchant,account,req,result,"WEB_BANK");
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String channelType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("MerchantTicketId", req.getOrderId());
        params.put("Account", account.getMerchantCode());
        params.put("ChannelType", channelType);
        params.put("Amount", req.getAmount().toString());
        params.put("MemberCode", "obama");
        params.put("AccessIP", "0.0.0.0");
        params.put("NotifyUrl", account.getNotifyUrl() + merchant.getId());
        params.put("ResType", "json");
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("Sign", MD5.md5(toSign).toUpperCase());
        if (StringUtils.equals("BANK_QRCODE", channelType)) {
            params.put("BankCode", channelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
            params.put("ResolvedUrl", "https://www.ballbet5.com/");
        }
        log.info("ShubaolaiPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.SHUBAOLAI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SHUBAOLAI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/Merchant/Channel?Alg=MD5", JSONObject.toJSONString(params), requestHeader);
        log.info("ShubaolaiPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if (!"Success".equals(json.getString("State"))) {
            throw new RuntimeException("创建订单失败");
        }
        String link = json.getJSONObject("Content").getJSONObject("TicketInfo").getString("Link");
        result.setRedirectUrl(link);
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("ShubaolaiPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String reqBody = resMap.get("reqBody");
        String orderId = JSONObject.parseObject(reqBody).getString("CusId");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("Account", account.getMerchantCode());
        params.put("MerchantTicketId", orderId);
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("Sign", MD5.md5(toSign).toUpperCase());
        log.info("ShubaolaiPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SHUBAOLAI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SHUBAOLAI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/Merchant/Fetch/Ticket?Alg=MD5", JSONObject.toJSONString(params), requestHeader);
        log.info("ShubaolaiPayer_query_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resp);
        // 请求成功 并且 支付成功
        if ("Success".equals(json.getString("State")) && "RESOLVED".equals(json.getJSONObject("Content").getString("Status"))) {
            RechargeNotify pay = new RechargeNotify();
            json = json.getJSONObject("Content");
            pay.setAmount(json.getBigDecimal("RealAmount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("SLBTicketId"));
            return pay;
        }

        return null;
    }
}
