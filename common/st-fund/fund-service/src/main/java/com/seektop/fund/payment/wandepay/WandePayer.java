package com.seektop.fund.payment.wandepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
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
 * 万德支付
 *
 */
@Slf4j
@Service(FundConstant.PaymentChannel.WANDEPAY + "")
public class WandePayer implements GlPaymentRechargeHandler {

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
        String payType = "";
        if(merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY){
            payType = "902";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY){
            payType = "903";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY){
            payType = "907";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY){
            payType = "918";
        }
        if(StringUtils.isNotEmpty(payType)){
            prepareScan(merchant, payment, req, result,payType);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>();
            DataContentParms.put("pay_memberid", payment.getMerchantCode());
            DataContentParms.put("pay_bankcode", payType);//支付编码
            DataContentParms.put("pay_orderid", req.getOrderId());
            DataContentParms.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            DataContentParms.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            DataContentParms.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId());
            DataContentParms.put("pay_callbackurl", payment.getNotifyUrl() + merchant.getId());

            String toSign = MD5.toAscii(DataContentParms) + "&key=" + payment.getPrivateKey();
            log.info("pay_md5sign:{}",toSign);
            DataContentParms.put("pay_md5sign", MD5.md5(toSign).toUpperCase());
            DataContentParms.put("pay_productname", "recharge");//商品名称

            log.info("WandePayer_Prepare_Params:{}", JSON.toJSONString(DataContentParms));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html" ,DataContentParms,requestHeader);
            log.info("WandePayer_Prepare_resStr:{}", restr);

            if(StringUtils.isEmpty(restr)){
                throw new GlobalException("创建订单失败");
            }

            if(merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY
                    ||  merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY
                    || merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY){
                JSONObject json = JSON.parseObject(restr);
                if(json == null){
                    throw new GlobalException("创建订单失败");
                }
                if(json.getString("code").equals("200")){
                    result.setRedirectUrl(json.getString("payurl"));
                }
            }else{
                result.setMessage(restr);
            }
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
        log.info("WandePayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderid");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("pay_memberid", account.getMerchantCode());
        DataContentParms.put("pay_orderid", orderId);

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + account.getPrivateKey();
        DataContentParms.put("pay_md5sign", MD5.md5(toSign).toUpperCase());

        log.info("WandePayer_Query_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", DataContentParms, requestHeader);
        log.info("WandePayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("00".equals(json.getString("returncode")) && "SUCCESS".equals(json.getString("trade_state"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("transaction_id"));
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
                .channelId(PaymentMerchantEnum.WANDE_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WANDE_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
