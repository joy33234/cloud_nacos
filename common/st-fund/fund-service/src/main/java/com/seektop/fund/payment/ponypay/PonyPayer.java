package com.seektop.fund.payment.ponypay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
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
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 立马付支付
 *
 * @author rick
 */
@Slf4j
@Service(FundConstant.PaymentChannel.PONY_PAY + "")
public class PonyPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    /**
     * 封装支付请求参数
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        // 商家密钥
        String keyValue = payment.getPrivateKey();

        String merchantId = payment.getMerchantCode();// 商户编号
        String orderId = req.getOrderId();// 订单号
        String paytype = null;//支付方式

        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            paytype = "WX";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paytype = "WXH5";
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {// 支付宝
            paytype = "ZFB";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {// 支付宝 H5
                paytype = "ZFBH5";
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) { // 云闪付
            paytype = "YSF";
        }

        // 服务端通知
        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        // 页面跳转通知
        String callbackurl = payment.getResultUrl() + merchant.getId();
        // 客户IP地址
        String userip = req.getIp();
        // 订单金额
        BigDecimal money = req.getAmount();

        String toSign = merchantId + orderId + paytype + notifyurl + callbackurl + money + keyValue;
        log.info("PonyPayer_Prepare_toSign:{}", toSign);
        String sign = MD5.md5(toSign);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("merchant_id", merchantId);
        paramMap.put("orderid", orderId);
        paramMap.put("paytype", paytype);
        paramMap.put("notifyurl", notifyurl);
        paramMap.put("callbackurl", callbackurl);
        paramMap.put("userip", userip);
        paramMap.put("money", money.toString());
        paramMap.put("sign", sign);

        log.info("PonyPayer_Prepare_paramMap:{}", paramMap);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.PONY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.PONY_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(payment.getPayUrl(), paramMap, requestHeader);

        log.info("PonyPayer_Prepare_resStr: {}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败-充值订单请求失败");
        }

        JSONObject json = JSON.parseObject(resStr);
        if (null == json || !"1".equals(json.getString("status"))) {
            String message = null == json ? "充值订单请求失败" : json.getString("message");
            throw new RuntimeException("创建订单失败-" + message);
        }

        JSONObject dataObj = json.getJSONObject("data");
        // 跳转地址
        result.setRedirectUrl(dataObj.get("url").toString());
    }

    /**
     * 支付返回结果校验
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        log.info("PonyPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderid");
        if (StringUtils.isEmpty(orderid)) {
            return null;
        }
        return query(payment, orderid);
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount merchant, String orderId) {
        String keyValue = merchant.getPrivateKey(); // 商家密钥
        String merchantId = merchant.getMerchantCode(); // 商户编号

        String toSign = merchantId + orderId + keyValue;

        String sign = MD5.md5(toSign);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("merchant_id", merchantId);
        paramMap.put("orderid", orderId);
        paramMap.put("sign", sign);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.PONY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.PONY_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/search.aspx", paramMap, requestHeader);
        log.info("PonyPayer_Query_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !json.getString("status").equals("1")) {
            return null;
        }
        JSONObject dataObj = json.getJSONObject("data");
        if (!dataObj.getString("status").equals("1")) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(dataObj.getBigDecimal("money"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(dataObj.getString("orderid"));
        return pay;

    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) {
        // 商户密钥
        String key = merchantAccount.getPrivateKey();
        //   商户号
        String merchant_id = merchantAccount.getMerchantCode();
        //  异步通知地址
        String notifyurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();
        // 提交地址IP
        String userip = req.getIp();

        // data  付款数据（json） 此数据请urlencode
        BigDecimal money = req.getAmount().subtract(req.getFee()).add(BigDecimal.valueOf(5));

        Map<String, String> dataParam = new HashMap<>();
        dataParam.put("corderid", req.getOrderId());//订单号
        dataParam.put("money", money.toString());
        dataParam.put("bankcode", req.getCardNo());//银行帐号
        try {
            dataParam.put("bankName", URLEncoder.encode(req.getBankName(), "UTF-8"));//银行帐号
            dataParam.put("bankusername", URLEncoder.encode(req.getName(), "UTF-8"));//开户人名称
            dataParam.put("bankaddress", URLEncoder.encode("上海市", "UTF-8"));//开户地址
        } catch (UnsupportedEncodingException e) {
            log.info("PonyPayer_encode_Error:{}", e);
        }

        log.info("PonyPayer_doTransfer_dataParam:{}", dataParam);

        String data = JSON.toJSONString(dataParam);

        // 加密数据
        String toSign = merchant_id + notifyurl + userip + data + key;
        String sign = MD5.md5(toSign).toLowerCase();

        String param = "merchant_id=" + merchant_id + "&notifyurl=" + notifyurl + "&userip=" + userip + "&data=" + data + "&sign=" + sign;

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(param);

        log.info("PonyPayer_doTransfer_paramMap:{}", param);

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.PONY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.PONY_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/payforcustom.aspx", param, requestHeader);
        log.info("PonyPayer_doTransfer_resStr:{}", resStr);

        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject resObj = JSON.parseObject(resStr);
        if (null == resObj || !"1".equals(resObj.getString("status"))) {//正确
            result.setValid(false);
            result.setMessage(resObj.get("message").toString());
            return result;
        }

        JSONObject dataObj = resObj.getJSONObject("data");
        if (!"1".equals(dataObj.get("status"))) {
            result.setValid(false);
            result.setMessage(dataObj.get("message").toString());
            return result;

        }
        result.setValid(true);
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) {
        log.info("PonyPayer_doTransferNotify_resMap:{}", resMap);
        String orderId = resMap.get("corderid");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return doTransferQuery(merchant, orderId);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount payment, String orderId) {
        String keyValue = payment.getPrivateKey();
        String merchant_id = payment.getMerchantCode();

        StringBuffer toSign = new StringBuffer();
        toSign.append(merchant_id);
        toSign.append(orderId);
        toSign.append(keyValue);

        String sign = MD5.md5(toSign.toString());

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("merchant_id", merchant_id);
        paramMap.put("orderid", orderId);
        paramMap.put("sign", sign);

        log.info("PonyPayer_doTransferQuery_paramMap:{}", paramMap);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.PONY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.PONY_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(payment.getPayUrl() + "/payforresearch.aspx", paramMap, requestHeader);
        log.info("PonyPayer_doTransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }

        JSONObject data = json.getJSONObject("data");
        WithdrawNotify glWithdrawNotify = new WithdrawNotify();
        if ("1".equals(data.getString("status"))) {
            glWithdrawNotify.setStatus(0);
        } else if ("2".equals(data.getString("status"))) {
            glWithdrawNotify.setStatus(1);
        } else {
            glWithdrawNotify.setStatus(2);
        }
        glWithdrawNotify.setMerchantCode(data.getString("merchant_id"));
        glWithdrawNotify.setOrderId(data.getString("corderid"));
        glWithdrawNotify.setAmount(data.getBigDecimal("money"));
        return glWithdrawNotify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) {
        String merchant_id = merchantAccount.getMerchantCode();

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("merchant_id", merchant_id);

        log.info("PonyPayer_queryBalance_paramMap:{}", paramMap);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.PONY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.PONY_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/getbalance.aspx", paramMap, requestHeader);

        log.info("PonyPayer_queryBalance_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"1".equals(json.getString("status"))) {
            return BigDecimal.ZERO;
        }

        if (json.getJSONObject("data") == null) {
            return BigDecimal.ZERO;
        }

        return json.getJSONObject("data").getBigDecimal("account");
    }
}
