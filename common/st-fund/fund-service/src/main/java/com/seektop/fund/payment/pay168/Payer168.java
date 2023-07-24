package com.seektop.fund.payment.pay168;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.HtmlTemplateUtils;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 168支付
 *
 * @author eric
 */
@Slf4j
@Service(FundConstant.PaymentChannel.P168_PAY + "")
public class Payer168 implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

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

        String keyValue = payment.getPrivateKey(); // 商家密钥

        String pay_memberid = payment.getMerchantCode();
        String pay_orderid = req.getOrderId();
        String pay_applydate = DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS);
        String pay_bankcode = "";
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            pay_bankcode = "2315";
        }
        String pay_notifyurl = payment.getNotifyUrl() + merchant.getId();
        String pay_callbackurl = payment.getResultUrl() + merchant.getId();
        String pay_amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString();
        String pay_attach = "";//附加字段  此字段在返回时按原样返回 (中文需要url编码) 非必填不参与签名
        String pay_productname = "CZ";//商品名称  必填不参与签名
        String pay_productnum = "";//商户品数量  非必填不参与签名
        String pay_productdesc = "";//商品描述  非必填不参与签名

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("pay_memberid", pay_memberid);
        paramMap.put("pay_orderid", pay_orderid);
        paramMap.put("pay_applydate", pay_applydate);
        paramMap.put("pay_bankcode", pay_bankcode);
        paramMap.put("pay_notifyurl", pay_notifyurl);
        paramMap.put("pay_callbackurl", pay_callbackurl);
        paramMap.put("pay_amount", pay_amount);

        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        log.info("Payer168_Prepare_toSign: {}", toSign);
        String pay_md5sign = MD5.md5(toSign).toUpperCase();

        paramMap.put("pay_md5sign", pay_md5sign);
        paramMap.put("pay_productname", pay_productname);
        if (StringUtils.isNotEmpty(pay_attach)) {
            paramMap.put("pay_attach", pay_attach);
        } else if (StringUtils.isNotEmpty(pay_productnum)) {
            paramMap.put("pay_productnum", pay_productnum);
        } else if (StringUtils.isNotEmpty(pay_productdesc)) {
            paramMap.put("pay_productdesc", pay_productdesc);
        }
        log.info("Payer168_Prepare_Params: {}", JSON.toJSONString(paramMap));
        result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/Pay_Index.html", paramMap));
        log.info("Payer168_Prepare_Message: {}", result.getMessage());
    }

    /**
     * 支付返回结果校验
     * n
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        log.info("Payer168_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderid");
        String returncode = resMap.get("returncode");
        if ("00".equals(returncode) && StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        }
        return null;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        // 请求地址 https://168.linkbtc.co/Pay_Trade_query.html
        String keyValue = payment.getPrivateKey(); // 商家密钥

        String pay_memberid = payment.getMerchantCode();
        String pay_orderid = orderId;

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("pay_memberid", pay_memberid);
        paramMap.put("pay_orderid", pay_orderid);

        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        log.info("Payer168_Query_toSign: {}", toSign);
        String pay_md5sign = MD5.md5(toSign).toUpperCase();
        paramMap.put("pay_md5sign", pay_md5sign);
        String queryUrl = payment.getPayUrl() + "/Pay_Trade_query.html";
        log.info("Payer168_Query_Params: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.P168_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.P168_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String result = okHttpUtil.post(queryUrl, paramMap, requestHeader);
        log.info("Payer168_Query_resStr: {}", result);

        JSONObject json = JSON.parseObject(result);
        if (json == null) {
            return null;
        }
        String returncode = json.getString("returncode");
        String trade_state = json.getString("trade_state");
        if ((!"00".equals(returncode)) || !"SUCCESS".equals(trade_state)) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(BigDecimal.valueOf(Double.valueOf(json.getString("amount"))));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("transaction_id"));
        return pay;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {

        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥

        String pay_memberid = merchantAccount.getMerchantCode();//商户号
        String pay_out_trade_no = req.getOrderId();
        String pay_money = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString();
        String pay_bankname = paymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId());
        String pay_subbranch = "支行";
        String pay_accountname = req.getName();
        String pay_cardnumber = req.getCardNo();
        String pay_province = "上海市"; //省
        String pay_city = "上海市";//城市
        String pay_notifyurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("pay_memberid", pay_memberid);
        paramMap.put("pay_out_trade_no", pay_out_trade_no);
        paramMap.put("pay_money", pay_money);
        paramMap.put("pay_bankname", pay_bankname);
        paramMap.put("pay_subbranch", pay_subbranch);
        paramMap.put("pay_accountname", pay_accountname);
        paramMap.put("pay_cardnumber", pay_cardnumber);
        paramMap.put("pay_province", pay_province);
        paramMap.put("pay_city", pay_city);
        paramMap.put("pay_notifyurl", pay_notifyurl);
        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        log.info("Payer168_Transfer_toSign: {}", toSign);
        String pay_md5sign = MD5.md5(toSign).toUpperCase();
        paramMap.put("pay_md5sign", pay_md5sign);
        log.info("Payer168_Transfer_Params: {}", JSON.toJSONString(paramMap));
        // 请求地址 https://168.linkbtc.co/Payment_Dfpay_add.html
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.P168_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.P168_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", paramMap, requestHeader);
        log.info("Payer168_Transfer_resStr: {}", resStr);

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
        if (json == null || !"success".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("msg"));
        result.setThirdOrderId(json.getString("transaction_id"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("Payer168_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderid");// 商户订单号
        if (StringUtils.isNotEmpty(orderid)) {
            return doTransferQuery(merchant, orderid);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {

        String keyValue = merchant.getPrivateKey(); // 商家密钥
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("pay_memberid", merchant.getMerchantCode());
        paramMap.put("pay_out_trade_no", orderId);

        String signInfo = MD5.toAscii(paramMap);
        signInfo = signInfo + "&key=" + keyValue;
        log.info("Payer168_Transfer_Query_toSign: {}", signInfo);
        String pay_md5sign = MD5.md5(signInfo).toUpperCase();
        paramMap.put("pay_md5sign", pay_md5sign);

        log.info("Payer168_Transfer_Query_Params: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.P168_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.P168_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", paramMap, requestHeader);
        log.info("Payer168_Transfer_Query_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"success".equals(json.getString("status"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("amount"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark(json.getString("refMsg"));
        if ("1".equals(json.getString("refCode"))) {
            notify.setStatus(0);
        } else if ("2".equals(json.getString("refCode"))) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }

        notify.setThirdOrderId(json.getString("out_trade_no"));
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {

        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥
        String pay_noncestr = UUID.randomUUID().toString().replace("-", "");
        String pay_memberid = merchantAccount.getMerchantCode();

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("pay_memberid", pay_memberid);
        paramMap.put("pay_noncestr", pay_noncestr);

        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        log.info("Payer168_Query_Balance_toSign: {}", toSign);
        String pay_md5sign = MD5.md5(toSign.toString()).toUpperCase();
        paramMap.put("pay_md5sign", pay_md5sign);

        //请求地址 https://168.linkbtc.co/Pay_Balance_query.html
        log.info("Payer168_Query_Balance_Params: {}", JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.P168_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.P168_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post("https://168.linkbtc.co/Pay_Balance_query.html", paramMap, requestHeader);
        log.info("Payer168_Query_Balance_resStr: {}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal balance = json.getBigDecimal("balance");
        return balance == null ? BigDecimal.ZERO : balance;
    }

}
