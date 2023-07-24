package com.seektop.fund.payment.huoyipay;

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
 * 火翼支付
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HUOYIPAY + "")
public class HuoYiPayer implements GlPaymentRechargeHandler {


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
        String service = "";
        if(merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY){
            service = "105";
        }
        if(StringUtils.isNotEmpty(service)){
            prepareScan(merchant, payment, req, result,service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("mchId", payment.getMerchantCode());
            params.put("productId", service);
            params.put("mchOrderNo", req.getOrderId());
            params.put("amount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
            params.put("returnUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("subject", "recharge");
            params.put("body", "recharge");
            params.put("clientIp", req.getIp());

            String toSign = MD5.toAscii(params) + "&key=" + payment.getPrivateKey();
            params.put("sign", MD5.md5(toSign).toUpperCase());

            log.info("HuoYiPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/api/pay/create" ,params,requestHeader);
            log.info("HuoYiPayer_Prepare_resStr:{}", restr);

            JSONObject json = JSONObject.parseObject(restr);

            if(json == null || !json.getString("retCode").equals("SUCCESS") || StringUtils.isEmpty(json.getString("payUrl"))){
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(json.getString("payUrl"));

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
        log.info("HuoYiPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("mchOrderNo");
        } else {
            orderId = json.getString("mchOrderNo");
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
        params.put("mchId", account.getMerchantCode());
        params.put("mchOrderNo", orderId);

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign).toUpperCase());

        log.info("HuoYiPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/pay/query", params, requestHeader);
        log.info("HuoYiPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"SUCCESS".equals(json.getString("retCode"))) {
            return null;
        }
        if ("2".equals(json.getString("status")) || "3".equals(json.getString("status"))) {// 支付状态,0=订单生成,1=支付中,2=支付成功,3=业务处理完成
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("payOrderId"));
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
                .channelId(PaymentMerchantEnum.HUOYI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUOYI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
