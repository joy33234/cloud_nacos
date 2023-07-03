package com.seektop.fund.payment.hengxingpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 恒星支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HENGXINGPAY + "")
public class HengxingPayer implements GlPaymentRechargeHandler {


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
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {

        Map<String, String> DataContentParms = new LinkedHashMap<>();
        DataContentParms.put("mcnNum", payment.getMerchantCode());
        DataContentParms.put("orderId", req.getOrderId());
        DataContentParms.put("backUrl", payment.getNotifyUrl() + merchant.getId());
        if (req.getClientType() == ProjectConstant.ClientType.PC) {//PC
            DataContentParms.put("payType", "2");
        } else {//移动
            DataContentParms.put("payType", "12");
        }
        DataContentParms.put("amount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());

        String toSign = MD5.toSign(DataContentParms) + "&secreyKey=" + payment.getPrivateKey();
        log.info("tosign:{}", toSign);
        DataContentParms.put("ip", req.getIp());
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase());
        log.info("sign:{}", MD5.md5(toSign).toUpperCase());


        log.info("HengxingPayer_Prepare_Params:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/api/v1/pay_qrcode.api", JSON.toJSONString(DataContentParms), requestHeader);
        log.info("HengxingPayer_Prepare_resStr:{}", restr);

        JSONObject json = JSON.parseObject(restr);
        if (json == null || !json.getString("status").equals("0")) {
            throw new GlobalException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("qrCode"));

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
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        log.info("HengxingPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("orderId");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new LinkedHashMap<>();
        DataContentParms.put("mcnNum", account.getMerchantCode());
        DataContentParms.put("orderId", orderId);

        String toSign = MD5.toSign(DataContentParms) + "&secreyKey=" + account.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase());

        log.info("HengxingPayer_Query_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/v1/query_record.api", JSON.toJSONString(DataContentParms), requestHeader);
        log.info("HengxingPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !json.getString("status").equals("0")) {
            return null;
        }
        json = json.getJSONObject("content");
        if (json != null && "1".equals(json.getString("payStatus"))) {// 成功：1 支付失败：2
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(0, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
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
                .channelId(PaymentMerchantEnum.HENGXING_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HENGXING_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
