package com.seektop.fund.payment.joypay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
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
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.JOYPAY + "")
public class JoyPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

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
            service = "bank";
        }
        if(StringUtils.isNotEmpty(service)){
            prepareScan(merchant, payment, req, result,service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("fxid", payment.getMerchantCode());
            params.put("fxddh", req.getOrderId());
            params.put("fxdesc", "recharge");
            params.put("fxbankcode", paymentChannelBankBusiness.getBankCode(req.getBankId(),payment.getChannelId()));
            params.put("fxfee", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            params.put("fxnotifyurl", payment.getNotifyUrl() + merchant.getId());
            params.put("fxbackurl", payment.getNotifyUrl() + merchant.getId());
            params.put("fxpay", service);
            params.put("fxip", req.getIp());
            params.put("fxnotifystyle", "2");//回调返回json数据

            String toSign = payment.getMerchantCode() + req.getOrderId() + params.get("fxfee") + params.get("fxnotifyurl") + payment.getPrivateKey();
            params.put("fxsign", MD5.md5(toSign));

            log.info("===========JoyPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay" ,params,requestHeader);
            log.info("===========JoyPayer_Prepare_resStr:{}", restr);

            JSONObject json = JSON.parseObject(restr);
            if(json == null || !json.getString("status").equals("1") || StringUtils.isEmpty(json.getString("payurl"))){//状态【1代表正常】【0代表错误】
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(json.getString("payurl"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        log.info("========JoyPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderid = json.getString("fxddh");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("fxid", account.getMerchantCode());
        params.put("fxaction", "orderquery");
        params.put("fxddh", orderId);

        String toSign = account.getMerchantCode() + orderId + params.get("fxaction") + account.getPrivateKey();
        params.put("fxsign", MD5.md5(toSign));

        log.info("===========JoyPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay", params, requestHeader);
        log.info("===========JoyPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("1".equals(json.getString("fxstatus"))) {// 支付状态【1正常支付】【0支付异常】
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("fxfee").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("fxorder"));
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
                .channelId(PaymentMerchantEnum.JOY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JOY_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
