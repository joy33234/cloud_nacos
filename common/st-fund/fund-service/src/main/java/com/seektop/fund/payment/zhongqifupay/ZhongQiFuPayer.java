package com.seektop.fund.payment.zhongqifupay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
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
import java.util.HashMap;
import java.util.Map;

/**
 * 众企付支付
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.ZHONGQIFUPAY + "")
public class ZhongQiFuPayer implements GlPaymentRechargeHandler {


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
            service = "907";
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY){
            service = "916";
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER){
            service = "903";
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY){
            service = "903";
            if(req.getClientType() != ProjectConstant.ClientType.PC){
                service = "904";
            }
        }
        if(StringUtils.isNotEmpty(service)){
            prepareScan(merchant, payment, req, result,service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("pay_memberid", payment.getMerchantCode());
            params.put("pay_orderid", req.getOrderId());
            params.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            params.put("pay_bankcode", service);
            params.put("pay_notifyurl", payment.getNotifyUrl() + merchant.getId());
            params.put("pay_callbackurl", payment.getNotifyUrl() + merchant.getId());
            params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            String toSign = MD5.toAscii(params) + "&key=" + payment.getPrivateKey();
            params.put("pay_md5sign", MD5.md5(toSign).toUpperCase());
            params.put("pay_productname", "recharge");

            log.info("ZhongQifuPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay_Index.html" ,params,requestHeader);
            log.info("ZhongQifuPayer_Prepare_resStr:{}", restr);

            if(StringUtils.isEmpty(restr)){
                throw new GlobalException("创建订单失败");
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
        log.info("ZhongQifuPaye_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");

        if (StringUtils.isNotEmpty(orderId)) {
            return query(merchantaccount, orderId);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("pay_memberid", account.getMerchantCode());
        params.put("pay_orderid", orderId);

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("pay_md5sign", MD5.md5(toSign).toUpperCase());

        log.info("ZhongQifuPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", params, requestHeader);
        log.info("ZhongQifuPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("00".equals(json.getString("returncode")) && "SUCCESS".equals(json.getString("trade_state"))) {// NOTPAY-未支付 SUCCESS 已支付
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(BigDecimal.valueOf(Double.valueOf(json.getString("amount"))).setScale(2, RoundingMode.DOWN));
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
                .channelId(PaymentMerchantEnum.ZHONGQIFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHONGQIFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
