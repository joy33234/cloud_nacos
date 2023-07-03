package com.seektop.fund.payment.xinhuopay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ServiceException;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 新火支付
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.XINHUOPAY + "")
public class XinHuoPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlPaymentChannelBankBusiness channelBankBusiness;

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
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        if(merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY){
            prepareScan(merchant, payment, req, result);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            Map<String,String> params = new HashMap<String,String>();
            params.put("totalFee", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
            params.put("body", "recharge");
            params.put("charset", "utf-8");
            params.put("defaultbank", "");
            params.put("isApp", "web");
            params.put("merchantId", payment.getMerchantCode());
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("orderNo", req.getOrderId());
            params.put("paymentType", "1");
            params.put("paymethod", "directPay");
            params.put("returnUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("service", "online_pay");
            params.put("title", "recharge");

            String shastr = UtilSign.GetSHAstr(params, payment.getPrivateKey());
            params.put("signType", "SHA");
            params.put("sign", shastr);

            log.info("XInHuoPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/payment/v1/order/" + payment.getMerchantCode() + "-" + req.getOrderId(), params);
            log.info("XInHuoPayer_Prepare_resStr:{}", restr);

            if(StringUtils.isEmpty(restr)){
                throw new ServiceException("创建订单失败");
            }
            result.setMessage(restr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 解析支付结果
     *
     * @param merchant
     * @param merchantaccount
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, Map<String, String> resMap) throws GlobalException {
        log.info("XInHuoPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("order_no");
        } else {
            orderId = json.getString("order_no");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return query(merchantaccount, orderId);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("merchantId", account.getMerchantCode());
        params.put("orderNo", orderId);
        params.put("charset", "utf-8");

        String shastr = UtilSign.GetSHAstr(params, account.getPrivateKey());
        params.put("signType", "SHA");
        params.put("sign", shastr);

        String url = account.getPayUrl();
        if (url.contains("https")) {
            url = url.replace("https", "http");
        }
        log.info("XInHuoPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.get(url + "/payment/v1/order/" + account.getMerchantCode() + "-" + orderId, params, requestHeader);
        log.info("XInHuoPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"S0001".equals(json.getString("respCode"))) {
            return null;
        }
        if ("completed".equals(json.getString("status")) ) {//wait：等待支付，completed：支付成功，failed：支付失败
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("tradeNo"));
            return pay;
        }
        return null;
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
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, payment, resMap);
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.XINHUO_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINHUO_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
