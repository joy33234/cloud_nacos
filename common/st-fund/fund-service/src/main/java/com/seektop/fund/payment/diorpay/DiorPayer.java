package com.seektop.fund.payment.diorpay;

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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * DIORPAY支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.DIORPAY + "")
public class DiorPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

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
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            service = "YHKZK";
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_PAY) {
            service = "ALIPAY";
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.QUICK_ALI_PAY) {
            service = "ALIPAY_WAP";
        } else if(merchant.getPaymentId() == FundConstant.PaymentType.WECHAT_PAY || merchant.getPaymentId() == FundConstant.PaymentType.QUICK_WECHAT_PAY){
            service = "WECHAT";
        } else if(merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER){
            service = "YHKZK";
        }
        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> DataContentParms = new HashMap<String, String>();
            DataContentParms.put("MerchantCode", payment.getMerchantCode());
            DataContentParms.put("BankCode", service);
            DataContentParms.put("Amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            DataContentParms.put("OrderId", req.getOrderId());
            DataContentParms.put("NotifyUrl", payment.getNotifyUrl() + merchant.getId());
            DataContentParms.put("OrderDate", req.getCreateDate().getTime() + "");
            DataContentParms.put("Ip", req.getIp());


            String toSign = MD5.toAscii(DataContentParms) + "&Key=" + payment.getPrivateKey();
            DataContentParms.put("Sign", encode(toSign).toLowerCase());


            log.info("DiorPayer_Prepare_Params:{}", JSON.toJSONString(DataContentParms));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/api/pay", DataContentParms, requestHeader);
            log.info("DiorPayer_Prepare_resStr:{}", restr);

            JSONObject json = JSON.parseObject(restr);

            if (json == null || !json.getString("resultCode").equals("200")) {
                throw new GlobalException("创建订单失败");
            }

            json = json.getJSONObject("data").getJSONObject("data");
            if (json.getString("type").equals("url") || json.getString("type").equals("img")) {
                result.setRedirectUrl(json.getString("info"));
            } else if (json.getString("type").equals("string")) {
                result.setMessage(json.getString("info"));
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
        log.info("DiorPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("OrderId");
        if (StringUtils.isNotEmpty(orderId)) {
            return query(payment, orderId);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("MerchantCode", account.getMerchantCode());
        DataContentParms.put("OrderId", orderId);
        DataContentParms.put("Time", System.currentTimeMillis() + "");

        String toSign = MD5.toAscii(DataContentParms) + "&Key=" + account.getPrivateKey();
        DataContentParms.put("Sign", encode(toSign).toLowerCase());

        log.info("DiorPayer_Query_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/orderquery", DataContentParms, requestHeader);
        log.info("DiorPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !json.getString("resultCode").equals("200")) {
            return null;
        }
        json = json.getJSONObject("data").getJSONObject("data");

        if ("1".equals(json.getString("status"))) {//  0待处理，1完成，2失败
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("money").setScale(2, RoundingMode.DOWN));
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

    static char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public final static String encode(String s) {
        try {
            byte[] btInput = s.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {//高低位分开处理
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
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
                .channelId(PaymentMerchantEnum.DIOR_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.DIOR_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<>();
        DataContentParms.put("MerchantCode", merchantAccount.getMerchantCode());
        DataContentParms.put("OrderId", req.getOrderId());
        DataContentParms.put("BankCardNum", req.getCardNo());
        DataContentParms.put("BankCardName", req.getName());
        DataContentParms.put("Branch", "上海市");
        DataContentParms.put("BankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(),merchantAccount.getChannelId()));
        DataContentParms.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        DataContentParms.put("OrderDate", req.getCreateDate().getTime() + "");

        String toSign = MD5.toAscii(DataContentParms) + "&Key="  + merchantAccount.getPrivateKey();
        DataContentParms.put("Sign", encode(toSign).toLowerCase());

        DataContentParms.put("NotifyUrl", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());
        DataContentParms.put("Province", "上海市");
        DataContentParms.put("City", "上海市");
        DataContentParms.put("Area", "上海市");


        log.info("DiorPayer_Transfer_params: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw" , DataContentParms, requestHeader);
        log.info("DiorPayer_Transfer_resStr: {}", resStr);


        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(DataContentParms));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"200".equals(json.getString("resultCode")) || !json.getBoolean("success")) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("resultMsg"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("DiorPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("OrderId");
        if (StringUtils.isNotEmpty(orderid)) {
            return doTransferQuery(merchant, orderid);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("MerchantCode", merchant.getMerchantCode());
        DataContentParms.put("Time", System.currentTimeMillis() + "");
        DataContentParms.put("OrderId", orderId);

        String toSign = MD5.toAscii(DataContentParms) + "&Key="  + merchant.getPrivateKey();
        DataContentParms.put("Sign", encode(toSign).toLowerCase());

        log.info("DiorPayer_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/withdrawquery", DataContentParms, requestHeader);
        log.info("DiorPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("200".equals(json.getString("resultCode")) && json.getBoolean("success")) {
            JSONObject dataJSON = json.getJSONObject("data").getJSONObject("data");
            notify.setAmount(dataJSON.getBigDecimal("moneyReceived"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            notify.setThirdOrderId("");
            if (dataJSON.getString("status").equals("2")) {//商户返回出款状态：0成功，1失败,2处理中      三方订单状态 0待处理，1处理中，2完成，3失败
                notify.setStatus(0);
            } else if (dataJSON.getString("status").equals("3")) {
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
        DataContentParms.put("MerchantCode", merchantAccount.getMerchantCode());
        DataContentParms.put("Time", System.currentTimeMillis() + "");

        String toSign = MD5.toAscii(DataContentParms) + "&Key="  + merchantAccount.getPrivateKey();
        DataContentParms.put("Sign", encode(toSign).toLowerCase());

        log.info("DiorPayer_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/balancequery", DataContentParms, requestHeader);
        log.info("DiorPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if ("200".equals(json.getString("resultCode")) && json.getBoolean("success")) {
            JSONObject dataJson = json.getJSONObject("data").getJSONObject("data");
            return dataJson.getBigDecimal("dfamount") == null ? BigDecimal.ZERO : dataJson.getBigDecimal("dfamount");
        }
        return BigDecimal.ZERO;
    }
}
