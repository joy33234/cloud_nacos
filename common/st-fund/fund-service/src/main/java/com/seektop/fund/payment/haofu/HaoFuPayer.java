package com.seektop.fund.payment.haofu;

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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 豪富支付 ： 银联扫码、支付宝、微信
 *
 * @author darren
 * @create 2019-04-29
 */

@Slf4j
@Service(FundConstant.PaymentChannel.HAOFU_PAY_112 + "")
public class HaoFuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            prepareToAlipay(merchant, account, req, result);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNIONPAY_SACN) {
            prepareToUnionPay(merchant, account, req, result);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            prepareToWeixin(merchant, account, req, result);
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            prepareTransfer(merchant, account, req, result,"card");
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            prepareTransfer(merchant, account, req, result,"");
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            prepareTransfer(merchant, account, req, result,"");
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            prepareTransfer(merchant, account, req, result,"ali");
        }
    }

    private void prepareToAlipay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result)
            throws GlobalException {
        String key = account.getPrivateKey();
        String partner = account.getMerchantCode();
        String amount = req.getAmount().toString();
        String request_time = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
        String trade_no = req.getOrderId();
        String notify_url = account.getNotifyUrl() + merchant.getId();
        String pay_type = "sm";
        if (req.getClientType() != ProjectConstant.ClientType.PC) {
            pay_type = "h5";
        }
        Map<String, String> params = new HashMap<>();
        params.put("partner", partner);
        params.put("amount", amount);
        params.put("request_time", request_time);
        params.put("trade_no", trade_no);
        params.put("notify_url", notify_url);
        params.put("pay_type", pay_type);
        String toSign = MD5.toAscii(params) + "&" + key;
        log.info("HaoFuPayer_recharge_AliPay_signStr:{}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("HaoFuPayer_recharge_Alipay_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/payCenter/aliPay2", params, requestHeader);
        log.info("HaoFuPayer_recharge_Alipay_result:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"T".equals(json.getString("is_success"))) {
            throw new RuntimeException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("result"));
    }

    private void prepareToUnionPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result)
            throws GlobalException {
        String key = account.getPrivateKey();

        String partner = account.getMerchantCode();
        String amount = req.getAmount().toString();
        String request_time = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
        String trade_no = req.getOrderId();
        String callback_url = account.getResultUrl() + merchant.getId();
        String notify_url = account.getNotifyUrl() + merchant.getId();

        Map<String, String> params = new HashMap<>();
        params.put("partner", partner);
        params.put("amount", amount);
        params.put("request_time", request_time);
        params.put("trade_no", trade_no);
        params.put("callback_url", callback_url);
        params.put("notify_url", notify_url);
        String toSign = MD5.toAscii(params) + "&" + key;

        log.info("HaoFuPayer_recharge_unionPay_signStr:{}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("HaoFuPayer_recharge_unionPay_params:{}", JSON.toJSONString(params));
        result.setMessage(HtmlTemplateUtils.getPost(account.getPayUrl() + "/payCenter/unionqrpay", params));
        log.info("HaoFuPayer_recharge_unionPay_result:{}", result.getMessage());
    }

    private void prepareToWeixin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result)
            throws GlobalException {
        String key = account.getPrivateKey();
        String partner = account.getMerchantCode();
        String amount = req.getAmount().toString();
        String request_time = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
        String trade_no = req.getOrderId();
        String notify_url = account.getNotifyUrl() + merchant.getId();
        String pay_type = "sm";
        if (req.getClientType() != ProjectConstant.ClientType.PC) {
            pay_type = "h5";
        }
        Map<String, String> params = new HashMap<>();
        params.put("partner", partner);
        params.put("amount", amount);
        params.put("request_time", request_time);
        params.put("trade_no", trade_no);
        params.put("notify_url", notify_url);
        params.put("pay_type", pay_type);
        String toSign = MD5.toAscii(params) + "&" + key;
        log.info("HaoFuPayer_recharge_Weixin_signStr:{}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("HaoFuPayer_recharge_Weixin_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/payCenter/wxPay", params, requestHeader);
        log.info("HaoFuPayer_recharge_Weixin_result:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"T".equals(json.getString("is_success"))) {
            throw new RuntimeException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("result"));
    }

    private void prepareTransfer(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType)
            throws GlobalException {
        String url =  "/payCenter/gatewaypay";
        String key = account.getPrivateKey();
        String partner = account.getMerchantCode();
        String amount = req.getAmount().toString();
        String request_time = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
        String trade_no = req.getOrderId();
        String notify_url = account.getNotifyUrl() + merchant.getId();

        Map<String, String> params = new HashMap<>();
        params.put("partner", partner);
        params.put("amount", amount);
        params.put("request_time", request_time);
        params.put("trade_no", trade_no);
        params.put("notify_url", notify_url);
        params.put("callback_url", notify_url);
        if(merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY){
            url = "/payCenter/unionqrpay";
        }else {
            params.put("type", payType);
        }
        params.put("buyer", req.getFromCardUserName());

        String toSign = MD5.toAscii(params) + "&" + key;
        log.info("======_HaoFu_recharge_AliPay_signStr:{}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("======_HaoFu_recharge_transfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + url, params, requestHeader);
        log.info("======_HaoFu_recharge_transfer_result:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        result.setMessage(resStr);
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("HaoFuPayer_prepare_notify_resMap:{}", resMap);
        String orderId = resMap.get("out_trade_no");
        String status = resMap.get("status");
        if (!"2".equals(status) && StringUtils.isNotEmpty(orderId)) {
            return query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String key = account.getPrivateKey();
        String url = account.getPayUrl() + "/payCenter/orderQuery";

        Map<String, String> params = new HashMap<>();
        params.put("partner", account.getMerchantCode());
        params.put("request_time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        params.put("out_trade_no", orderId);
        String toSign = MD5.toAscii(params) + "&" + key;
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);
        log.info("HaoFuPayer_Query_Params_:{}", params);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(url, params, requestHeader);
        JSONObject json = JSON.parseObject(resStr);
        log.info("HaoFuPayer_Query_response_:{}", json);
        if (json == null || !"T".equals(json.getString("is_success"))
                || !json.getString("status").equals("1")) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(json.getBigDecimal("amount_str"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("trade_id"));
        return pay;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String key = merchantAccount.getPrivateKey();

        Map<String, String> params = new HashMap<>();
        params.put("partner", merchantAccount.getMerchantCode());
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("request_time", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS));
        params.put("trade_no", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        params.put("bank_sn", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("bank_site_name", "Site");
        params.put("bank_account_name", req.getName());
        params.put("bank_province", "Province");
        params.put("bank_city", "City");
        params.put("bank_account_no", req.getCardNo());
        params.put("remark", "TX");
        params.put("bus_type", "0");
        params.put("bank_mobile_no", "13800138000");

        String toSign = MD5.toAscii(params) + "&" + key;

        log.info("HaoFuPayer_transfer_toSign:{}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);

        log.info("HaoFuPayer_transfer_param:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/payCenter/agentPay", params, requestHeader);
        log.info("HaoFuPayer_transfer_response:{}", JSON.toJSONString(resStr));
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("第三方接口错误");
            return result;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"T".equals(json.getString("is_success"))) {
            result.setValid(false);
            result.setMessage(json == null ? "第三方接口错误" : json.getString("fail_msg"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("fail_msg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("HaoFuPayer_doTransferNotify_resMap:{}", resMap);
        String orderId = resMap.get("out_trade_no");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String key = merchant.getPrivateKey();
        String url = merchant.getPayUrl() + "/payCenter/orderQuery";

        Map<String, String> params = new HashMap<>();
        params.put("partner", merchant.getMerchantCode());
        params.put("request_time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        params.put("out_trade_no", orderId);
        String toSign = MD5.toAscii(params) + "&" + key;
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);
        log.info("HaoFuPayer_transfer_query_Params_:{}", params);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(url, params, requestHeader);
        JSONObject json = JSON.parseObject(resStr);
        log.info("HaoFuPayer_transfer_query_Response : {}", json);
        if (json == null || !"T".equals(json.getString("is_success"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("amount_str"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark(json.getString("fail_msg"));

        if (json.getString("status").equals("1")) {
            notify.setStatus(0);
        } else if (json.getString("status").equals("2")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }

        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String key = merchantAccount.getPrivateKey();
        String url = merchantAccount.getPayUrl() + "/payCenter/account";
        Map<String, String> params = new HashMap<>();
        params.put("partner", merchantAccount.getMerchantCode());
        params.put("request_time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        String toSign = MD5.toAscii(params) + "&" + key;
        String sign = MD5.md5(toSign).toLowerCase();
        params.put("sign", sign);
        log.info("HaoFuPayer_Query_Balance_Params_:{}", params);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_112.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_112.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(url, params, requestHeader);
        JSONObject json = JSON.parseObject(resStr);
        log.info("HaoFuPayer_Query_Balance_Response_:{}", json);
        if (json == null || !"T".equals(json.getString("is_success"))) {
            return BigDecimal.ZERO;
        }
        return json.getBigDecimal("account");
    }
}
