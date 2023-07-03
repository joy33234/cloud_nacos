package com.seektop.fund.payment.yinshanfupay;

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
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.YINSHANFUPAY + "")
public class YinshanfuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY || merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY
                || merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER || merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            String payType;
            if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY) {
                payType = "M0720003";
            } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
                payType = "M1260001";
            } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
                payType = "M1260002";
            } else {
                payType = "M0720002";
            }
            prepareToScan(merchant, account, req, result, payType);
        }

    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("key", account.getMerchantCode());
        params.put("method", payType);
        params.put("trade_no", req.getOrderId());
        params.put("title", "CZ");
        params.put("memo", "CZ");
        params.put("money", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("notify", account.getNotifyUrl() + merchant.getId());
        params.put("redirect", account.getResultUrl() + merchant.getId());
        String toSign = MD5.toAscii(params);
        toSign += "&secret=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YinshanfuPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.YINSHANFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YINSHANFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay", params, requestHeader);
        log.info("XingPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if ("SUCCESS".equals(json.getString("status")) && "0".equals(json.getString("code"))) {
            result.setRedirectUrl(json.getString("page_info"));
        }


    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("YinshanfuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("trade_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("key", account.getMerchantCode());
        params.put("trade_no", orderId);
        params.put("timestamp", System.currentTimeMillis() + "");
        String toSign = MD5.toAscii(params);
        toSign += "&secret=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YinshanfuPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.YINSHANFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YINSHANFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/query", params, requestHeader);
        log.info("YinshanfuPayer_query_resp:{}", resStr);
        JSONObject json = JSONObject.parseObject(resStr);
        if ("SUCCESS".equals(json.getString("status")) && ("SUCCESS".equals(json.getString("trade_status"))
                || "Notifying".equals(json.getString("trade_status")) || "NotifyFail".equals(json.getString("trade_status")))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("money").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("sn"));
            return pay;
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("key", merchantAccount.getMerchantCode());
        params.put("trade_no", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("card_number", req.getCardNo());
        params.put("bank_name", req.getBankName());
        params.put("sub_bank_name", "上海市支行");
        params.put("account_name", req.getName());
        params.put("province", "上海市");
        params.put("city", "上海市");
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("memo", "TX");
        String toSign = MD5.toAscii(params);
        toSign += "&secret=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YinshanfuPayer_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.YINSHANFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YINSHANFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/entpay", params, requestHeader);
        log.info("YinshanfuPayer_doTransfer_resp:{}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if ("FAIL".equals(json.getString("status")) || !"0".equals(json.getString("code"))) {
            result.setValid(false);
            result.setMessage(unicodeToUtf8(json.getString("message")));
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(unicodeToUtf8(json.getString("message")));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("YinshanfuPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("trade_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("key", merchant.getMerchantCode());
        params.put("trade_no", orderId);
        params.put("timestamp", System.currentTimeMillis() + "");
        String toSign = MD5.toAscii(params);
        toSign += "&secret=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YinshanfuPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.YINSHANFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YINSHANFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/entquery", params, requestHeader);
        log.info("YinshanfuPayer_doTransferQuery_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject retJson = JSON.parseObject(resStr);
        if (!"SUCCESS".equals(retJson.getString("status")) || !retJson.getString("code").equals("0")) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(retJson.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(0, RoundingMode.DOWN));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(retJson.getString("trade_no"));
        notify.setThirdOrderId("");
        if (retJson.getString("trade_status").equals("FINISH")) {
            notify.setStatus(0);
        } else if (retJson.getString("trade_status").equals("CLOSE")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("key", merchantAccount.getMerchantCode());
        params.put("timestamp", System.currentTimeMillis() + "");
        String toSign = MD5.toAscii(params);
        toSign += "&secret=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YinshanfuPayer_queryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.YINSHANFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YINSHANFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balance", params, requestHeader);
        log.info("YinshanfuPayer_doTransferQuery_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json.getString("status").equals("SUCCESS") && json.getString("code").equals("0")) {
            return json.getBigDecimal("all_can_request_amount");
        }

        return null;
    }

    /**
     * unicode转换字符串
     */
    public static String unicodeToUtf8(String unicode) {
        if (StringUtils.isEmpty(unicode)) {
            return null;
        }
        String str = "";
        String[] hex = unicode.split("//u");
        System.out.println(hex.length);
        for (int i = 1; i < hex.length; i++) {
            int data = Integer.parseInt(hex[i], 16);
            str += (char) data;
        }
        return str;
    }
}
