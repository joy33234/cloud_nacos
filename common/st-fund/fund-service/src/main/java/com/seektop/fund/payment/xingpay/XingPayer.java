package com.seektop.fund.payment.xingpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service(FundConstant.PaymentChannel.XINGPAY + "")
public class XingPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    private static final String SERVER_PAY_URL = "/api/order/pay";

    private static final String SERVER_QUERY_URL = "/api/order/getByOutTradeNo";

    private static final String SERVER_DF_PAY_URL = "/api/tikuan/withdraw";

    private static final String SERVER_DF_QUERY_URL = "/api/tikuan/getByOutTradeNo";

    private static final String SERVER_ACCOUNT_QUERY_URL = "/api/account/get";


    @Resource
    private GlPaymentChannelBankBusiness channelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;


    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            String payType = "";
            if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
                if (req.getClientType() != ProjectConstant.ClientType.PC) {
                    if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
                        payType = "6022";
                    } else {
                        payType = "6012";
                    }
                }
            } else {
                payType = "6051";
            }
            if (StringUtils.isEmpty(payType)) {
                return;
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("sid", account.getMerchantCode());
        params.put("payType", payType);
        params.put("amount", req.getAmount().toString());
        params.put("outTradeNo", req.getOrderId());
        params.put("orderType", "2"); // 充值
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        String toSign = MD5.toAscii(params);
        toSign += "@@" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("XingPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.XING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XING_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, params, requestHeader);
        log.info("XingPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSON.parseObject(resStr);
        if ("500".equals(json.getString("code")) || !json.getBoolean("result")) {
            throw new RuntimeException("创建订单失败");
        }
        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            result.setRedirectUrl(json.getJSONObject("data").getString("payUrl"));
        } else {
            result.setMessage(getQRCode("data:image/png;base64," + json.getJSONObject("data").getString("qrCode")));
        }

    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("XingPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("out_trade_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("sid", account.getMerchantCode());
        params.put("outTradeNo", orderId);
        String toSign = MD5.toAscii(params);
        toSign += "@@" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("XingPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_QUERY_URL, params, requestHeader);
        log.info("XingPayer_query_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        // 请求成功 并且 支付成功
        if (json.getBoolean("result") && "12".equals(json.getJSONObject("data").getString("status"))) {
            RechargeNotify pay = new RechargeNotify();
            json = json.getJSONObject("data");
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("tradeNo"));
            return pay;
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("sid", merchantAccount.getMerchantCode());
        params.put("bankName", channelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        params.put("accountNo", req.getCardNo());
        params.put("accountName", req.getName());
        params.put("amount", req.getAmount().subtract(req.getFee()).toString());
        params.put("payNotifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("userTransNo", req.getOrderId());
        String toSign = MD5.toAscii(params);
        toSign += "@@" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("XingPayer_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.XING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XING_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_DF_PAY_URL, params, requestHeader);
        log.info("XingPayer_doTransfer_resp:{}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (null == resStr) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        JSONObject json = JSON.parseObject(resStr);
        if ("500".equals(json.getString("code")) || !json.getBoolean("result")) {
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
        log.info("XingPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("out_trade_no");
        if (null != orderId && !"".equals(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("sid", merchant.getMerchantCode());
        params.put("outTradeNo", orderId);
        String toSign = MD5.toAscii(params);
        toSign += "@@" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("XingPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_DF_QUERY_URL, params, requestHeader);
        log.info("XingPayer_doTransferQuery_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject retJson = JSON.parseObject(resStr);
        if ("500".equals(retJson.getString("code")) || retJson.getString("result").equals("false")) {
            return null;
        }
        retJson = retJson.getJSONObject("data");
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(retJson.getBigDecimal("amount"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(retJson.getString("outTradeNo"));
        notify.setThirdOrderId(retJson.getString("tradeNo"));
        if (retJson.getString("status").equals("15")) {
            notify.setStatus(0);
        } else if (retJson.getString("status").equals("16")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new TreeMap<>();
        params.put("sid", merchantAccount.getMerchantCode());
        String toSign = MD5.toAscii(params);
        toSign += "@@" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("XingPayer_queryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.XING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XING_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_ACCOUNT_QUERY_URL, params, requestHeader);
        log.info("XingPayer_queryBalance_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (!json.getString("result").equals("false")) {
            return json.getJSONObject("data").getBigDecimal("balance");
        }
        return BigDecimal.ZERO;
    }


    public static String getQRCode(String qrCode) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("<html>");
            sb.append("<head><meta http-equiv='Content-Type' content='text/html; charset=GBK'></head>");
            sb.append("<script type='text/javascript' src='https://cdn.staticfile.org/jquery/2.1.1/jquery.min.js'></script>");
            sb.append("<script type='text/javascript' src='https://cdn.staticfile.org/jquery.qrcode/1.0/jquery.qrcode.min.js'></script>");
            sb.append("<body style=\"margin: 0px; background: #0e0e0e;display: flex;align-items: center;\">");
            sb.append("<div id='qrcode' style=\"margin: auto;width: 385px;height: 385px;border:8px solid #fff;\">");
            sb.append("<img src='").append(qrCode).append("' style=\"width:385px;height:385px\"/>");
            sb.append("</div>");
            sb.append("</body>");
            sb.append("</html>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sb = null;
        }
    }
}
