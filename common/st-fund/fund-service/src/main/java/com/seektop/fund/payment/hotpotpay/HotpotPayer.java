package com.seektop.fund.payment.hotpotpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 锅子支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HOTPOTPAY + "")
public class HotpotPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {

        String pay_bankcode = "";

        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            pay_bankcode = "926";
            if (ProjectConstant.ClientType.PC != req.getClientType()) {
                pay_bankcode = "922";
            }
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            pay_bankcode = "924";
        }

        Map<String, String> param = new HashMap<>();
        param.put("pay_memberid", account.getMerchantCode());
        param.put("pay_orderid", req.getOrderId());
        param.put("pay_applydate", DateUtils.formatForTime(new Date()));
        param.put("pay_bankcode", pay_bankcode);
        param.put("pay_notifyurl", account.getNotifyUrl() + merchant.getId());
        param.put("pay_callbackurl", account.getResultUrl() + merchant.getId());
        param.put("pay_amount", req.getAmount().toString());

        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();

        log.info("HotpotPayer_prepare_toSign:{}", toSign);

        String sign = MD5.md5(toSign).toUpperCase();

        param.put("pay_md5sign", sign);
        param.put("pay_attach", req.getUsername());
        param.put("pay_productname", "CZ");
        log.info("HotpotPayer_prepare_param:{}", param);

        result.setMessage(HtmlTemplateUtils.getPost(account.getPayUrl() + "/Pay_Index.html", param));
        log.info("HotpotPayer_prepare_resStr:{}", result.getMessage());
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("HotpotPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return query(account, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> param = new HashMap<>();
        param.put("pay_memberid", account.getMerchantCode());
        param.put("pay_orderid", orderId);
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();
        log.info("HotpotPayer_query_toSign:{}", toSign);

        String sign = MD5.md5(toSign).toUpperCase();
        param.put("pay_md5sign", sign);

        log.info("HotpotPayer_query_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HOTPOT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HOTPOT_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", param, requestHeader);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        log.info("HotpotPayer_query_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"00".equals(json.getString("returncode"))) {
            return null;
        }

        if (!"SUCCESS".equals(json.getString("trade_state"))) {
            return null;
        }

        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(json.getBigDecimal("amount"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("transaction_id"));
        return pay;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) throws GlobalException {

        Map<String, String> param = new HashMap<>();
        param.put("mchid", account.getMerchantCode());
        param.put("out_trade_no", req.getOrderId());
        param.put("money", req.getAmount().subtract(req.getFee()).toString());
        param.put("bankname", req.getBankName());
        param.put("subbranch", "支行名称");
        param.put("accountname", req.getUsername());
        param.put("cardnumber", req.getCardNo());
        param.put("province", "上海");
        param.put("city", "上海");
        param.put("extends", "");

        String toSign = MD5.toAscii(param) + "&key=" + account.getPublicKey();
        log.info("HotpotPayer_doTransfer_toSign:{}", toSign);
        String sign = MD5.md5(toSign).toUpperCase();

        param.put("pay_md5sign", sign);

        log.info("HotpotPayer_doTransfer_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.HOTPOT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HOTPOT_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Payment_Dfpay_add.html", param, requestHeader);
        log.info("HotpotPayer_doTransfer_resStr:{}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(param));

        if (StringUtils.isEmpty(resStr)) {
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return result;
        }

        String status = json.getString("status");
        result.setMessage(json.getString("msg"));
        result.setThirdOrderId(json.getString("transaction_id"));

        if ("error".equals(status)) {
            result.setValid(false);
            return result;
        }
        result.setValid(true);
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount account, Map<String, String> resMap) throws GlobalException {
        log.info("HotpotPayer_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("out_trade_no");
        if (org.springframework.util.StringUtils.isEmpty(orderId)) {
            return null;
        }
        return doTransferQuery(account, orderId);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount account, String orderId) throws GlobalException {
        Map<String, String> param = new HashMap<>();
        param.put("out_trade_no", orderId);
        param.put("mchid", account.getMerchantCode());

        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();
        log.info("HotpotPayer_doTransferQuery_toSign:{}", toSign);
        String sign = MD5.md5(toSign).toUpperCase();
        param.put("pay_md5sign", sign);

        log.info("HotpotPayer_doTransferQuery_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HOTPOT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HOTPOT_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Payment_Dfpay_query.html", param, requestHeader);
        log.info("HotpotPayer_doTransferQuery_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }

        if (!"success".equals(json.getString("status"))) {
            return null;
        }

        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("amount"));
        notify.setMerchantCode(account.getMerchantCode());
        notify.setMerchantId(account.getMerchantId());
        notify.setMerchantName(account.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark(json.getString("refMsg"));
        if ("1".equals(json.getString("refCode"))) {
            notify.setStatus(0);
            notify.setSuccessTime(json.getDate("success_time"));
        } else if ("2".equals(json.getString("refCode"))) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        notify.setThirdOrderId(json.getString("transaction_id"));
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount account) throws GlobalException {
        return BigDecimal.ZERO;
    }
}
