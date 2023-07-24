package com.seektop.fund.payment.julipay;

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
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.JULIPAY + "")
public class JuliPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankService;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Integer payMethod = null;
        if (Arrays.asList(FundConstant.PaymentType.WECHAT_PAY, FundConstant.PaymentType.ALI_PAY,
                FundConstant.PaymentType.QQ_PAY, FundConstant.PaymentType.ALI_TRANSFER).contains(merchant.getPaymentId())) {
            if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                payMethod = 0;
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                payMethod = 1;
            } else if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
                payMethod = 5;
            } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                payMethod = 14;
            }
            prepareToScan(merchant, account, req, result, payMethod);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, Integer payMethod) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("businessId", account.getMerchantCode());
        paramsMap.put("signType", "MD5");
        paramsMap.put("uid", req.getUsername());
        paramsMap.put("amount", req.getAmount().toString());
        paramsMap.put("outTradeNo", req.getOrderId());
        paramsMap.put("random", System.currentTimeMillis() + "");
        paramsMap.put("payMethod", payMethod + "");
        paramsMap.put("dataType", "0"); // 返回json数据格式
        paramsMap.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        paramsMap.put("returnUrl", account.getNotifyUrl() + merchant.getId());
        paramsMap.put("secret", account.getPrivateKey());
        String signStrTemp = MD5.toAscii(paramsMap);
        paramsMap.put("sign", MD5.md5(signStrTemp));
        log.info("JuliPayer_recharge_prepare_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/createPayOrder", paramsMap, requestHeader);
        log.info("JuliPayer_recharge_prepare_result:{}", resStr);
        JSONObject json = JSONObject.parseObject(resStr);
        if (StringUtils.isEmpty(resStr) || !json.getBoolean("successed")) {
            throw new RuntimeException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("returnValue"));
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JuliPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("outTradeNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("businessId", account.getMerchantCode());
        paramsMap.put("signType", "MD5");
        paramsMap.put("outTradeNo", orderId);
        paramsMap.put("random", System.currentTimeMillis() + "");
        paramsMap.put("secret", account.getPrivateKey());
        String signStrTemp = MD5.toAscii(paramsMap);
        paramsMap.put("sign", MD5.md5(signStrTemp));
        log.info("JuliPayer_query_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay/queryPayOrder", paramsMap, requestHeader);
        log.info("JuliPayer_query_result:{}", resStr);
        JSONObject json = JSONObject.parseObject(resStr);
        // 查询成功 并且支付成功
        if (json.getBoolean("successed") && "2".equals(json.getJSONObject("returnValue").getString("orderState"))) {
            JSONObject data = json.getJSONObject("returnValue");
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(data.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(data.getString("oid"));
            return pay;
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("businessId", merchantAccount.getMerchantCode());
        paramsMap.put("signType", "MD5");
        paramsMap.put("amount", req.getAmount().subtract(req.getFee()).toString());
        paramsMap.put("outTradeNo", req.getOrderId());
        paramsMap.put("cardNumber", req.getCardNo());
        paramsMap.put("cardholder", req.getName());
        paramsMap.put("openBank", paymentChannelBankService.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        paramsMap.put("branchBank", "上海市");
        paramsMap.put("random", System.currentTimeMillis() + "");
        paramsMap.put("secret", merchantAccount.getPrivateKey());
        paramsMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        String signStrTemp = MD5.toAscii(paramsMap);
        paramsMap.put("sign", MD5.md5(signStrTemp));
        log.info("JuliPayer_doTransfer_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/applyCashWithdrawal", paramsMap, requestHeader);
        log.info("JuliPayer_doTransfer_result:{}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(paramsMap));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (!json.getBoolean("successed")) {
            result.setValid(false);
            result.setMessage(json.getString("errorDesc"));
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(json.getString("errorDesc"));
        return result;
    }


    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("JuliPayer_doTransferNotify_params:{}", resMap);
        String orderId = resMap.get("outTradeNo");
        if (!StringUtils.isEmpty(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("businessId", merchant.getMerchantCode());
        paramsMap.put("signType", "MD5");
        paramsMap.put("outTradeNo", orderId);
        paramsMap.put("secret", merchant.getPrivateKey());
        paramsMap.put("random", System.currentTimeMillis() + "");
        String signStrTemp = MD5.toAscii(paramsMap);
        paramsMap.put("sign", MD5.md5(signStrTemp));
        log.info("JuliPayer_doTransferQuery_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/pay/queryCashWithdrawalOrder", paramsMap, requestHeader);
        log.info("JuliPayer_doTransferQuery_result:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (!json.getBoolean("successed")) {
            return null;
        }
        JSONObject returnValue = json.getJSONObject("returnValue");
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(returnValue.getBigDecimal("amount"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setThirdOrderId(returnValue.getString("oid"));
        if (returnValue.getString("orderState").equals("2")) {
            notify.setStatus(0);
        } else if (returnValue.getString("orderState").equals("4")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("businessId", merchantAccount.getMerchantCode());
        paramsMap.put("signType", "MD5");
        paramsMap.put("secret", merchantAccount.getPrivateKey());
        paramsMap.put("random", System.currentTimeMillis() + "");
        String signStrTemp = MD5.toAscii(paramsMap);
        paramsMap.put("sign", MD5.md5(signStrTemp));
        log.info("JuliPayer_queryBalance_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.JULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JULI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay/queryFinanceInfo", paramsMap, requestHeader);
        log.info("JuliPayer_queryBalance_result:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json.getBoolean("successed")) {
            return json.getJSONObject("returnValue").getBigDecimal("availableMoney");
        }
        return BigDecimal.ZERO;
    }
}
