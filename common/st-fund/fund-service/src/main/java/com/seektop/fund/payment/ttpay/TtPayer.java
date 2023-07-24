package com.seektop.fund.payment.ttpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 泰坦支付
 *
 * @author tiger
 */
@Slf4j
@Service(FundConstant.PaymentChannel.TTPAY + "")
public class TtPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

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
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("version", "1.0.0");
            params.put("service", "trade.pay");
            params.put("charset", "UTF-8");
            params.put("signType", "RSA");
            params.put("reqSysTime", System.currentTimeMillis() + "");
            params.put("merNo", payment.getMerchantCode());
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("orderNo", req.getOrderId());
            params.put("totalAmount", req.getAmount().multiply(new BigDecimal(100)).setScale(0,RoundingMode.DOWN) + "");
            params.put("subject", "recharge");
            params.put("orderTime", req.getCreateDate().getTime() + "");
            params.put("productCode", "WY");//产品编码
            params.put("userId", req.getUserId() + "");
            params.put("cardType", "1");
            params.put("bankCode", "ICBC");
            params.put("extendParam", "张三");
            params.put("sign", SignUtils.sign(params, payment.getPrivateKey(), params.get("charset"), params.get("signType")));

            log.info("===========TtPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/pay-api/gateway" ,params,requestHeader);
            log.info("===========TtPayer_Prepare_resStr:{}", restr);

            JSONObject json = JSON.parseObject(restr);
            if(json == null || !json.getString("isSuccess").equals("S") || json.getJSONObject("msgFormat") == null){
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(json.getJSONObject("msgFormat").getString("pay_url"));
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
        log.info("========TtPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        JSONObject json = JSON.parseObject(reqBody);
        String orderid =  json.getString("outTradeNo");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {

        Map<String, String> params = new HashMap<String, String>();
        params.put("version", "1.0.0");
        params.put("service", "trade.query");
        params.put("charset", "UTF-8");
        params.put("signType", "RSA");
        params.put("reqSysTime", System.currentTimeMillis() + "");
        params.put("merNo", account.getMerchantCode());
        params.put("orderNo", orderId);
        params.put("sign", SignUtils.sign(params, account.getPrivateKey(), params.get("charset"), params.get("signType")));

        log.info("===========TtPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/pay-api/gateway", params, requestHeader);
        log.info("===========TtPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("000000".equals(json.getString("responseCode")) && "S".equals(json.getString("tradeStatus"))) {// 0: 交易查询N：新建；S：付款成功；C：订单关闭；
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("orderAmount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
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


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {

        Map<String, String> params = new HashMap<String, String>();
        params.put("version", "1.0.0");
        params.put("service", "fund.trans");
        params.put("charset", "UTF-8");
        params.put("signType", "RSA");
        params.put("reqSysTime", System.currentTimeMillis() + "");
        params.put("merNo", merchantAccount.getMerchantCode());
        params.put("orderNo", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0,RoundingMode.DOWN) + "");
        params.put("accType", "0");
        params.put("accName", req.getName());
        params.put("bankCard", req.getCardNo());
        params.put("bankCode", paymentChannelBankBusiness.getBankCode(req.getBankId(),merchantAccount.getChannelId()));
        params.put("bankName", req.getBankName());
        params.put("bankBranchName", "上海支行");
        params.put("province", "上海市");
        params.put("city", "上海市");
        params.put("orderTime", req.getCreateDate().getTime() + "");
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("sign", SignUtils.sign(params, merchantAccount.getPrivateKey(), params.get("charset"), params.get("signType")));


        log.info("=========TtPayer_Transfer_params: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay-api/gateway" , params, requestHeader);
        log.info("=========TtPayer_Transfer_resStr: {}", resStr);


        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"000000".equals(json.getString("responseCode"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("responseMsg"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("========TtPayer_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String reqBody = resMap.get("reqBody");
        if (StringUtils.isEmpty(reqBody)) {
            return null;
        }
        JSONObject json = JSON.parseObject(reqBody);
        String orderid =  json.getString("outTradeNo");
        if (StringUtils.isNotEmpty(orderid)) {
            return doTransferQuery(merchant, orderid);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("version", "1.0.0");
        params.put("service", "fund.trans.query");
        params.put("charset", "UTF-8");
        params.put("signType", "RSA");
        params.put("reqSysTime", System.currentTimeMillis() + "");
        params.put("merNo", merchant.getMerchantCode());
        params.put("orderNo", orderId);
        params.put("sign", SignUtils.sign(params, merchant.getPrivateKey(), params.get("charset"), params.get("signType")));

        log.info("===========TtPayer_TransferQuery_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/pay-api/gateway", params, requestHeader);
        log.info("===========TtPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("000000".equals(json.getString("responseCode"))) {
            notify.setAmount(json.getBigDecimal("amount").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(json.getString("orderNo"));
            notify.setThirdOrderId("");
            if (json.getString("tradeStatus").equals("1")) {//三方代付查询：1-成功，2-处理中，5-挂起，9-失败       商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0);
            } else if (json.getString("tradeStatus").equals("9")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("version", "1.0.0");
        params.put("service", "find.merchant.balance");
        params.put("charset", "UTF-8");
        params.put("signType", "RSA");
        params.put("merNo", merchantAccount.getMerchantCode());
        params.put("sign", SignUtils.sign(params, merchantAccount.getPrivateKey(), params.get("charset"), params.get("signType")));

        log.info("=========TtPayer_QueryBalance_reqMap: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/pay-api/gateway", params, requestHeader);
        log.info("==========TtPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json != null && "000000".equals(json.getString("responseCode"))) {
            BigDecimal amount = json.getBigDecimal("bal").divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
            return amount == null ? BigDecimal.ZERO : amount;
        }
        return BigDecimal.ZERO;
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
                .channelId(PaymentMerchantEnum.TT_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TT_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
