package com.seektop.fund.payment.xinfutong;

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

/**
 * 新信付通支付接口 20200306 商户切换新系统
 *
 * @author tiger
 */
@Slf4j
@Service(FundConstant.PaymentChannel.XINFUTONG + "")
public class XinFuTongPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

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
        String service = "";
        if(merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY){
            service = "10104";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY){
            service = "10106";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.QUICK_PAY){
            service = "10102";
        }else if(merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY){
            service = "10103";
        }
        if(StringUtils.isNotEmpty(service)){
            prepareScan(merchant, payment, req, result,service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>();
            DataContentParms.put("partner", payment.getMerchantCode());
            DataContentParms.put("service", service);
            DataContentParms.put("tradeNo", req.getOrderId());
            DataContentParms.put("amount", req.getAmount().setScale(2,RoundingMode.DOWN) + "");
            DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
            DataContentParms.put("resultType", payment.getPublicKey());//跳转方式  web/json  商户配置来控制

            String toSign = MD5.toAscii(DataContentParms) + "&" + payment.getPrivateKey();
            DataContentParms.put("sign", MD5.md5(toSign));

            log.info("XinFuTongPayer_Prepare_Params:{}", JSON.toJSONString(DataContentParms));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/unionOrder" ,DataContentParms,requestHeader);
            log.info("XinFuTongPayer_Prepare_resStr:{}", restr);

            if(StringUtils.isEmpty(restr)){
                throw new GlobalException("创建订单失败");
            }

            if("json".equals(payment.getPublicKey())){//根据成功率切换跳转方式
                JSONObject json = JSON.parseObject(restr);
                if(json == null){
                    throw new GlobalException("创建订单失败");
                }
                if(json.getString("isSuccess").equals("T")){
                    result.setRedirectUrl(json.getString("url"));
                }
            }else if("web".equals(payment.getPublicKey())){
                result.setMessage(restr);
            }else{
                throw new GlobalException("商户跳转方式配置错误");
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
        log.info("XinFuTongPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("outTradeNo");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("partner", account.getMerchantCode());
        DataContentParms.put("service", "10302");
        DataContentParms.put("outTradeNo", orderId);

        String toSign = MD5.toAscii(DataContentParms) + "&" + account.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign));

        log.info("XinFuTongPayer_Query_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orderQuery", DataContentParms, requestHeader);
        log.info("XinFuTongPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("T".equals(json.getString("isSuccess")) && "1".equals(json.getString("status"))) {// 0: 处理中   1：成功    2：失败
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

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<>();
        DataContentParms.put("partner", merchantAccount.getMerchantCode());
        DataContentParms.put("service", "10201");
        DataContentParms.put("tradeNo", req.getOrderId());
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        DataContentParms.put("bankCode", paymentChannelBankBusiness.getBankCode(req.getBankId(),merchantAccount.getChannelId()));
        DataContentParms.put("bankCardNo", req.getCardNo());
        DataContentParms.put("bankCardholder", req.getName());
        DataContentParms.put("subsidiaryBank", "上海市");
        DataContentParms.put("subbranch", "上海市");
        DataContentParms.put("province", "上海市");
        DataContentParms.put("city", "上海市");
        DataContentParms.put("notifyUrl", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchantAccount.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign));

        log.info("XinFuTongPayer_Transfer_params: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/agentPay" , DataContentParms, requestHeader);
        log.info("XinFuTongPayer_Transfer_resStr: {}", resStr);


        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(DataContentParms));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"T".equals(json.getString("isSuccess"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("XinFuTongPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("outTradeNo");
        if (StringUtils.isNotEmpty(orderid)) {
            return doTransferQuery(merchant, orderid);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("partner", merchant.getMerchantCode());
        DataContentParms.put("service", "10301");
        DataContentParms.put("outTradeNo", orderId);

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchant.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign));

        log.info("XinFuTongPayer_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/orderQuery", DataContentParms, requestHeader);
        log.info("XinFuTongPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("T".equals(json.getString("isSuccess"))) {
            notify.setAmount(json.getBigDecimal("amount"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(json.getString("outTradeNo"));
            notify.setThirdOrderId("");
            if (json.getString("status").equals("1")) {//订单状态判断标准： 0 处理中 1 成功 2 失败    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0);
            } else if (json.getString("status").equals("2")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<>();
        DataContentParms.put("partner", merchantAccount.getMerchantCode());
        DataContentParms.put("service", "10401");

        String toSign = MD5.toAscii(DataContentParms) + "&" + merchantAccount.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign));

        log.info("XinFuTongPayer_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balanceQuery", DataContentParms, requestHeader);
        log.info("XinFuTongPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json != null && "T".equals(json.getString("isSuccess"))) {
            return json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance");
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
                .channelId(PaymentMerchantEnum.XINFUTONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XINFUTONG_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
