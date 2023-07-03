package com.seektop.fund.payment.wuxingpay;

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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.WUXINGPAY + "")
public class WuxingPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() ||
                FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            String payType;
            if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                    || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
                payType = "0402";
            } else {
                payType = "0502";
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        String[] code = account.getMerchantCode().split("\\|\\|");
        Map<String, String> params = new HashMap<>();
        params.put("version", "3.0");
        params.put("orgNo", code[0]);
        params.put("custId", code[1]);
        params.put("custOrderNo", req.getOrderId());
        params.put("tranType", payType);
        params.put("openType", "1");
        params.put("payAmt", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("backUrl", account.getNotifyUrl() + merchant.getId());
        params.put("frontUrl", account.getResultUrl() + merchant.getId());
        String sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("WuxingPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.WUXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WUXING_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/tran/cashier/pay.ac", params, requestHeader);
        log.info("WuxingPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if ("000000".equals(json.getString("code"))) {
            result.setRedirectUrl(json.getString("busContent"));
        } else {
            throw new RuntimeException("创建订单失败");
        }
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("WuxingPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("custOrderNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String[] code = account.getMerchantCode().split("\\|\\|");
        Map<String, String> params = new HashMap<>();
        params.put("version", "3.0");
        params.put("orgNo", code[0]);
        params.put("custId", code[1]);
        params.put("custOrderNo", orderId);
        String sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("WuxingPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.WUXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WUXING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/tran/cashier/query.ac", params, requestHeader);
        log.info("WuxingPayer_query_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) return null;
        JSONObject json = JSONObject.parseObject(resStr);
        if ("000000".equals(json.getString("code")) && "01".equals(json.getString("ordStatus"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("ordAmt").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("tradeNo"));
            return pay;
        }

        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String[] code = merchantAccount.getMerchantCode().split("\\|\\|");
        Map<String, String> params = new HashMap<>();
        params.put("version", "3.0");
        params.put("orgNo", code[0]);
        params.put("custId", code[1]);
        params.put("custOrdNo", req.getOrderId());
        params.put("casType", "00");
        params.put("casAmt", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("accountName", req.getName());
        params.put("cardNo", req.getCardNo());
        String sign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("WuxingPayer_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.WUXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WUXING_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/tran/cashier/TX0001.ac", params, requestHeader);
        log.info("WuxingPayer_doTransfer_resp:{}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (!"000000".equals(json.getString("code"))) {
            result.setValid(false);
            result.setMessage(json.getString("msg"));
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(json.getString("msg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("WuxingPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("custOrderNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String[] code = merchant.getMerchantCode().split("\\|\\|");
        Map<String, String> params = new HashMap<>();
        params.put("version", "3.0");
        params.put("orgNo", code[0]);
        params.put("custId", code[1]);
        params.put("custOrdNo", orderId);
        String sign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("WuxingPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.WUXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WUXING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/tran/cashier/TX0002.ac", params, requestHeader);
        log.info("WuxingPayer_doTransferQuery_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject retJson = JSON.parseObject(resStr);
        if (!"000000".equals(retJson.getString("code"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(retJson.getBigDecimal("casAmt").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(retJson.getString("custOrdNo"));
        notify.setThirdOrderId(retJson.getString("custId"));
        if (retJson.getString("ordStatus").equals("07")) {
            notify.setStatus(0);
        } else if (retJson.getString("ordStatus").equals("08")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String[] code = merchantAccount.getMerchantCode().split("\\|\\|");
        Map<String, String> params = new HashMap<>();
        params.put("version", "3.0");
        params.put("orgNo", code[0]);
        params.put("custId", code[1]);
        String sign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("WuxingPayer_queryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.WUXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WUXING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/tran/cashier/balance.ac", params, requestHeader);
        log.info("WuxingPayer_queryBalance_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) return BigDecimal.ZERO;
        JSONObject retJson = JSON.parseObject(resStr);
        if (!"000000".equals(retJson.getString("code"))) return BigDecimal.ZERO;
        return retJson.getBigDecimal("acBal").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
    }
}
