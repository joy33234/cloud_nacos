package com.seektop.fund.payment.zbpay;

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
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 众宝支付
 *
 * @author Darren
 */
@Slf4j
@Service(FundConstant.PaymentChannel.ZBPAY + "")
public class ZbPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req,
                        GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            prepareScanAndWangyin(merchant, payment, req, result);
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            prepareToKuaiJie(merchant, payment, req, result);
        }
    }

    public void prepareScanAndWangyin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req,
                                      GlRechargeResult result) throws GlobalException {
        String merchantid = payment.getMerchantCode();
        String paytype = "";
        String bankCode = paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId());
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) { // 网银支付
            paytype = bankCode;
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) { // 支付宝扫码
            paytype = PayType.ZHIFUBAO_SCAN.getCode();
            if (req.getClientType() != 0) {
                paytype = PayType.ZHIFUBAO_H5.getCode();
            }
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNIONPAY_SACN) { // 银联扫码
            paytype = PayType.UNION_SCAN.getCode();
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY) {
            paytype = PayType.WEIXIN_SCAN.getCode();
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paytype = PayType.WEIXIN_H5.getCode();
            }
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.JD_PAY) {
            paytype = PayType.JD_SCAN.getCode();
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paytype = PayType.JD_H5.getCode();
            }
        }
        String amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString();
        String orderid = req.getOrderId();
        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        String request_time = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        String returnurl = payment.getResultUrl() + merchant.getId();
        String desc = "CZ";
        String key = payment.getPrivateKey();

        String signStr = String.format("merchantid=%s&paytype=%s&amount=%s&orderid=%s&notifyurl=%s&request_time=%s&key=%s", merchantid,
                paytype, amount, orderid, notifyurl, request_time, key);
        String sign = MD5.md5(signStr);

        Map<String, String> reqData = new HashMap<String, String>();
        reqData.put("merchantid", merchantid);
        reqData.put("paytype", paytype);
        reqData.put("amount", amount);
        reqData.put("orderid", orderid);
        reqData.put("notifyurl", notifyurl);
        reqData.put("request_time", request_time);
        reqData.put("returnurl", returnurl);
        reqData.put("desc", desc);
        reqData.put("sign", sign);
        String html = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/GateWay/Pay", reqData);
        result.setMessage(html);
        log.info("ZbPayer_Prepare_Html:{}", result.getMessage());
    }

    public static void prepareToKuaiJie(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req,
                                        GlRechargeResult result) {
        String merchantid = payment.getMerchantCode();
        String amount = String.valueOf(req.getAmount().intValue());
        String orderid = req.getOrderId();
        String notifyurl = payment.getNotifyUrl() + merchant.getId();
        String request_time = DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        String returnurl = payment.getResultUrl() + merchant.getId();
        String desc = "CZ"; // 备注消息
        String key = payment.getPublicKey();

        String signStr = String.format("merchantid=%s&amount=%s&orderid=%s&notifyurl=%s&request_time=%s&key=%s",
                merchantid, amount, orderid, notifyurl, request_time, key);
        String sign = MD5.md5(signStr);

        Map<String, String> reqData = new HashMap();
        reqData.put("merchantid", merchantid);
        reqData.put("amount", amount);
        reqData.put("orderid", orderid);
        reqData.put("notifyurl", notifyurl);
        reqData.put("request_time", request_time);
        reqData.put("returnurl", returnurl);
        reqData.put("desc", desc);
        reqData.put("sign", sign);
        String responseData = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/FastPay/Index", reqData);
        result.setMessage(responseData);
        log.info("ZbPayer_Prepare_Html:{}", result.getMessage());
    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment,
                                 Map<String, String> resMap) throws GlobalException {
        String orderid = resMap.get("orderid");
        String result = resMap.get("result");
        String amount = resMap.get("amount");
        String systemorderid = resMap.get("systemorderid");
        String completetime = resMap.get("completetime");
        String sign = resMap.get("sign");

        String signStr = String.format("orderid=%s&result=%s&amount=%s&systemorderid=%s&completetime=%s&key=%s",
                orderid, result, amount, systemorderid, completetime, payment.getPublicKey());
        String signLocal = MD5.md5(signStr);
        if (signLocal.equals(sign)) {
            return query(payment, orderid);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String merchantid = account.getMerchantCode();
        String orderid = orderId;
        String signStr = String.format("orderid=%s&merchantid=%s&key=%s", orderid, merchantid, account.getPrivateKey());
        String sign = MD5.md5(signStr);

        Map<String, String> reqData = new HashMap();
        reqData.put("merchantid", merchantid);
        reqData.put("orderid", orderid);
        reqData.put("sign", sign);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ZB_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZB_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.get(account.getPayUrl() + "/GateWay/Query", reqData, requestHeader);
        log.info("ZbPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (null == json || !json.getString("code").equals("0")) {
            return null;
        }

        JSONObject data = json.getJSONObject("obj");
        if (null == data || !data.getString("result").equals("1")) {
            return null;
        }

        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(data.getBigDecimal("amount"));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(data.getString("systemorderid"));
        return pay;
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment,
                                 Map<String, String> resMap) throws GlobalException {
        return notify(merchant, payment, resMap);
    }

}
