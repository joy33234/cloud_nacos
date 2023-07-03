package com.seektop.fund.payment.xinduobao;

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
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 鑫多宝
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.XINDUOBAO + "")
public class XinDuoBaoPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            prepareScan(merchant, payment, req, result);
        }
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            prepareWangyin(merchant, payment, req, result);
        }
        if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            prepareQuickPay(merchant, payment, req, result);
        }

    }

    public void prepareWangyin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String usercode = payment.getMerchantCode(); // 用户编号
        String customno = req.getOrderId(); // 商户订单号
        String productname = "CZ"; // 商品名称
        String money = req.getAmount().setScale(2, RoundingMode.DOWN).toString(); // 支付金额
        String sendtime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS); // 商品名称
        String bankcode = paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId());

        String pageurl = payment.getResultUrl() + merchant.getId();
        String backurl = payment.getResultUrl() + merchant.getId();
        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        String buyerip = req.getIp();

        String sign = MD5.md5(usercode + "|" + customno + "|" + bankcode + "|" + notifyurl + "|" + money + "|" + sendtime + "|" + buyerip + "|" + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("customno", customno);
        paramMap.put("productname", productname);
        paramMap.put("money", money);
        paramMap.put("sendtime", sendtime);
        paramMap.put("bankcode", bankcode);
        paramMap.put("pageurl", pageurl);
        paramMap.put("backurl", backurl);
        paramMap.put("notifyurl", notifyurl);
        paramMap.put("buyerip", buyerip);
        paramMap.put("sign", sign);

        log.info("XinDuoBaoPayer_prepareWangyin_request: {}", JSON.toJSONString(paramMap));
        result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/api/cashierpay", paramMap));
        log.info("XinDuoBaoPayer_prepareWangyin_html: {}", result.getMessage());
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String usercode = payment.getMerchantCode(); // 用户编号
        String customno = req.getOrderId(); // 商户订单号
        String productname = "CZ"; // 商品名称
        String money = req.getAmount().setScale(2, RoundingMode.DOWN).toString(); // 支付金额
        String scantype = "YLWG";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            scantype = "AP";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                scantype = "APH5";
            }
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            scantype = "WP";
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            scantype = "YL";
        }
        String sendtime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);

        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        String buyerip = req.getIp();

        String sign = MD5.md5(usercode + "|" + customno + "|" + scantype + "|" + notifyurl + "|" + money + "|" + sendtime + "|" + buyerip + "|" + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("customno", customno);
        paramMap.put("productname", productname);
        paramMap.put("money", money);
        paramMap.put("scantype", scantype);
        paramMap.put("sendtime", sendtime);
        paramMap.put("notifyurl", notifyurl);
        paramMap.put("buyerip", buyerip);
        paramMap.put("sign", sign);

        log.info("XinDuoBaoPayer_prepareScan_request: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();

        String url = payment.getPayUrl();
        if (url.contains("https")) {
            url = url.replace("https", "http");
        }
        String resStr = okHttpUtil.post(url + "/api/scanpay", paramMap, requestHeader);
        log.info("XinDuoBaoPayer_prepareScan_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        JSONObject data = json.getJSONObject("data");
        if (json.getBoolean("success") == false || data == null || StringUtils.isEmpty(data.getString("scanurl"))) {
            throw new RuntimeException("创建订单失败");
        }
        String scanurl = data.getString("scanurl");
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            result.setMessage(HtmlTemplateUtils.getQRCode(scanurl));
        }else {
            result.setRedirectUrl(scanurl);
        }
    }

    public void prepareQuickPay(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String usercode = payment.getMerchantCode(); // 用户编号
        String customno = req.getOrderId(); // 商户订单号
        String productname = "CZ"; // 商品名称
        String money = req.getAmount().setScale(2, RoundingMode.DOWN).toString(); // 支付金额
        String scantype = "KJ";
//        String cardno = req.getCardNo();
//        String mobile = "13888888888";
//        String idcard = "110101200001010101";
        String sendtime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS); // 商品名称
        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        String buyerip = req.getIp();

        String sign = MD5.md5(usercode + "|" + customno + "|" + scantype + "|" + notifyurl + "|" + money + "|" + sendtime + "|" + buyerip + "|" + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("customno", customno);
        paramMap.put("productname", productname);
        paramMap.put("money", money);
        paramMap.put("scantype", scantype);
//        paramMap.put("cardno", cardno);
//        paramMap.put("mobile", mobile);
//        paramMap.put("idcard", idcard);
        paramMap.put("sendtime", sendtime);
        paramMap.put("notifyurl", notifyurl);
        paramMap.put("buyerip", buyerip);
        paramMap.put("sign", sign);

        log.info("XinDuoBaoPayer_prepareQuickPay_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();

        String url = payment.getPayUrl();
        if (url.contains("https")) {
            url = url.replace("https", "http");
        }
        String resStr = okHttpUtil.post(url + "/api/scanpay", paramMap, requestHeader);
        log.info("XinDuoBaoPayer_prepareQuickPay_resStr: {}", JSON.toJSONString(resStr));

        JSONObject json = JSON.parseObject(resStr);
        JSONObject data = json.getJSONObject("data");
        if (json.getBoolean("success") == false || data == null || StringUtils.isEmpty(data.getString("scanurl"))) {
            throw new RuntimeException("创建订单失败");
        }
        String scanurl = data.getString("scanurl");
        result.setRedirectUrl(scanurl);
    }

    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        log.info("XinDuoBaoPayer_recharge_notify:{}", JSON.toJSONString(resMap));
        String orderno = resMap.get("customno");// 商户订单号
        String status = resMap.get("status");// 1充值成功，  2充值失败，其他处理中
        String usercode = resMap.get("usercode");//用户编号
        String thirdOrderId = resMap.get("orderno");//订单号
        String type = resMap.get("type");//支付类型
        String bankcode = resMap.get("bankcode");//银行编号
        String tjmoney = resMap.get("tjmoney");//提交支付金额
        String money = resMap.get("money");//结算金额
        String currency = resMap.get("currency");//1人民币

        if (StringUtils.isEmpty(orderno) || StringUtils.isEmpty(status) || !"1".equals(resMap.get("status"))) {
            return null;
        }
        String sign = MD5.md5(usercode + "|" + thirdOrderId + "|" + orderno + "|" + type + "|" + bankcode + "|" + tjmoney + "|" + money + "|" + status + "|" + currency + "|" + payment.getPrivateKey());
        if (sign.equals(resMap.get("sign"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(BigDecimal.valueOf(Double.valueOf(tjmoney)));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderno);
            pay.setThirdOrderId(thirdOrderId);
            return pay;
        }
        return null;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String usercode = payment.getMerchantCode(); // 用户编号
        String opttype = "1";
        String sendtime = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS); // 商品名称
        String sign = MD5.md5(usercode + "|" + opttype + "|" + orderId + "|" + sendtime + "|" + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("customno", orderId);
        paramMap.put("sendtime", sendtime);
        paramMap.put("opttype", opttype);
        paramMap.put("sign", sign);

        log.info("XinDuoBaoPayer_Query_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String url = payment.getPayUrl();
        if (url.contains("https")) {
            url = url.replace("https", "http");
        }
        String resStr = okHttpUtil.post(url + "/api/query", paramMap, requestHeader);
        log.info("XinDuoBaoPayer_Query_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);

        boolean success = json.getBoolean("success");
        if (success == false) {
            return null;
        }
        JSONObject data = json.getJSONObject("data");
        String status = data.getString("status");
        String customno = data.getString("customno");
        if (StringUtils.isNotEmpty(status) && status.equals("1") && StringUtils.isNotEmpty(customno) && customno.equals(orderId)) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(BigDecimal.valueOf(Double.valueOf(data.getString("tjmoney"))));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(data.getString("orderno"));
            return pay;
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String usercode = merchantAccount.getMerchantCode();
        String customno = req.getOrderId();
        String type = "1";
        String money = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString();
        String bankname = req.getBankName();
        String realname = req.getName();
        String idcard = "123456789012345678";
        String cardno = req.getCardNo();
        String sendtime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS); // 商品名称
        String notifyurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();
        String buyerip = req.getIp();

        String sign = MD5.md5(usercode + "|" + customno + "|" + type + "|" + cardno + "|" + idcard + "|" + money + "|" + sendtime + "|" + buyerip + "|" + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("customno", customno);
        paramMap.put("type", type);
        paramMap.put("money", money);
        paramMap.put("bankname", bankname);
        paramMap.put("realname", realname);
        paramMap.put("idcard", idcard);
        paramMap.put("cardno", cardno);
        paramMap.put("sendtime", sendtime);
        paramMap.put("notifyurl", notifyurl);
        paramMap.put("buyerip", buyerip);
        paramMap.put("sign", sign);

        log.info("XinDuoBaoPayer_Transfer_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", paramMap, requestHeader);// PayUrl格式待确认
        log.info("XinDuoBaoPayer_Transfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(paramMap));
        result.setResData(resStr);

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getBoolean("success") == null || json.getBoolean("success") == false) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("resultMsg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        String customno = resMap.get("customno");// 商户订单号
        String status = resMap.get("status");//交易状态
        if (StringUtils.isNotEmpty(status) && "3".equals(status) && StringUtils.isNotEmpty(customno)) {
            return doTransferQuery(merchant, customno);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Date now = new Date();
        String privateKey = merchant.getPrivateKey();
        String usercode = merchant.getMerchantCode();
        String opttype = "2";
        String customno = orderId;
        String sendtime = DateUtils.format(now, "yyyyMMddHHmmss");
        String signStr = usercode + "|" + opttype + "|" + customno + "|" + sendtime + "|" + privateKey;

        String sign = MD5.md5(signStr);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("opttype", opttype);
        paramMap.put("customno", customno);
        paramMap.put("sendtime", sendtime);
        paramMap.put("sign", sign);
        log.info("XinDuoBaoPayer_TransferQuery_reqMap: {}", signStr);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/query", paramMap, requestHeader);// PayUrl格式待确认
        log.info("XinDuoBaoPayer_TransferQuery_resStr: {}", signStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getJSONObject("data") == null || json.getBoolean("success") == null
                || json.getBoolean("success") == false || json.getBoolean("exception") == null
                || json.getBoolean("exception") == true) {
            return null;
        }
        JSONObject data = json.getJSONObject("data");
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(new BigDecimal(data.getString("tjmoney")));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setThirdOrderId(data.getString("orderno"));
        notify.setStatus(data.getIntValue("status") == 3 ? 0 : 1);
        if (notify.getStatus() != 0) {
            notify.setAmount(null);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Date now = new Date();
        String privateKey = merchantAccount.getPrivateKey();
        String usercode = merchantAccount.getMerchantCode();
        String opttype = "3";
        String sendtime = DateUtils.format(now, "yyyyMMddHHmmss");
        String signStr = usercode + "|" + opttype + "|" + sendtime + "|" + privateKey;

        String sign = MD5.md5(signStr);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("usercode", usercode);
        paramMap.put("opttype", opttype);
        paramMap.put("sendtime", sendtime);
        paramMap.put("sign", sign);
        log.info("XinDuoBaoPayer_QueryBalance_reqMap: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.XINDUOBAO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINDUOBAO_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/query", paramMap, requestHeader);// PayUrl格式待确认
        log.info("XinDuoBaoPayer_QueryBalance_resStr: {}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"true".equals(json.getString("success")) || !"false".equals(json.getString("exception"))) {
            return BigDecimal.ZERO;
        }
        JSONObject body = json.getJSONObject("data");
        BigDecimal Balance = body.getBigDecimal("balance");
        return Balance == null ? BigDecimal.ZERO : Balance;
    }

}
