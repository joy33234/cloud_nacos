package com.seektop.fund.payment.anjipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.ANJIPAY + "")
public class AnjiPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {

        String paymentName = "";
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            paymentName = "UNIONSCAN";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paymentName = "UNIONWAP";
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            paymentName = "ALIPAY";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                paymentName = "ALIH5";
            }
        }
        if (StringUtils.isEmpty(paymentName)) {
            result.setErrorCode(1);
            result.setErrorMsg("订单创建失败，不支持" + merchant.getPaymentName());
            return;
        }

        prepareToScan(merchant, account, req, result, paymentName);
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String paymentName) {
        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = account.getPrivateKey();
        param.put("Merchant_ID", account.getMerchantCode());
        param.put("Type", paymentName);
        param.put("Bankcode", paymentName);
        param.put("Merchant_Order", req.getOrderId());
        param.put("Notice_Url", account.getNotifyUrl() + merchant.getId());
        param.put("Amount", req.getAmount().multiply(new BigDecimal(100)).toString());
        param.put("Create_Time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        String signStrTemp = MD5.toAscii(param);
        param.put("Sign_Type", "MD5");
        param.put("Sign", MD5.md5(signStrTemp + keyValue).toUpperCase());
        log.info("AnjiPayer_prepare_params:{}", JSON.toJSONString(param));
        String resStr = HtmlTemplateUtils.getPost(account.getPayUrl() + "/pay.do", param);
        log.info("AnjiPayer_prepare_result:{}", resStr);
        result.setMessage(resStr);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("AnjiPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String status = resMap.get("Status");
        String orderId = resMap.get("Merchant_Order");
        String type = resMap.get("Type");

        if (!StringUtils.isEmpty(status)) {
            return queryForNoitify(account, orderId, type);
        }
        return null;
    }

    public RechargeNotify queryForNoitify(GlPaymentMerchantaccount account, String orderId, String Type) throws GlobalException {
        String keyValue = account.getPrivateKey();
        Map<String, String> param = new HashMap<>();
        param.put("Merchant_ID", account.getMerchantCode());
        param.put("Type", Type);
        param.put("Merchant_Order", orderId);
        String signStr = MD5.toAscii(param);
        param.put("Sign_Type", "MD5");
        param.put("Sign", MD5.md5(signStr + keyValue).toUpperCase());
        log.info("AnjiPayer_query_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ANJI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANJI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/query.do", param, requestHeader);
        log.info("AnjiPayer_query_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        String code = json.getString("code");
        if (StringUtils.isNotEmpty(code) && "00".equals(code) && "SUCCESS".equals(json.getString("Status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("Amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("Sys_Order"));
            return pay;
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        GlRecharge recharge = glRechargeBusiness.findById(orderId);
        if (null == recharge) {
            return null;
        }
        String paymentName = "";

        if (FundConstant.PaymentType.UNIONPAY_SACN == recharge.getPaymentId()) {
            paymentName = "UNIONSCAN";
            if (recharge.getClientType() != ProjectConstant.ClientType.PC) {
                paymentName = "UNIONWAP";
            }
        } else if (FundConstant.PaymentType.ALI_PAY == recharge.getPaymentId()) {
            paymentName = "ALIPAY";
            if (recharge.getClientType() != ProjectConstant.ClientType.PC) {
                paymentName = "ALIH5";
            }
        }

        String keyValue = account.getPrivateKey();
        Map<String, String> param = new HashMap<>();
        param.put("Merchant_ID", account.getMerchantCode());
        param.put("Type", paymentName);
        param.put("Merchant_Order", orderId);
        String signStr = MD5.toAscii(param);
        param.put("Sign_Type", "MD5");
        param.put("Sign", MD5.md5(signStr + keyValue).toUpperCase());
        log.info("AnjiPayer_query_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ANJI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANJI_PAY.getPaymentName())
                .userId(recharge.getUserId().toString())
                .userName(recharge.getUsername())
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/query.do", param, requestHeader);
        log.info("AnjiPayer_query_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        String code = json.getString("code");
        if (StringUtils.isNotEmpty(code) && "00".equals(code) && "SUCCESS".equals(json.getString("Status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("Amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("Sys_Order"));
            return pay;
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {

        String outSource = getOutSource(merchantAccount, req.getAmount());
        if (StringUtils.isEmpty(outSource)) {
            WithdrawResult result = new WithdrawResult();
            result.setValid(false);
            result.setMessage("余额不足，出款失败");
            return result;
        }
        Map<String, String> param = new LinkedHashMap<>();
        param.put("Merchant_ID", merchantAccount.getMerchantCode());
        param.put("Type", "WITHDRAWAL");
        param.put("Bankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        param.put("Merchant_Order", req.getOrderId());
        param.put("Amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0).toString());
        param.put("Create_Time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
        param.put("Bank_Card_Name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        param.put("Bank_Card_No", req.getCardNo());
        param.put("Bank_Card_Phone", "13611111482"); // 客户手机号码 固定值
        param.put("Bank_Card_Province", "北京市");
        param.put("Bank_Card_City", "北京市");
        param.put("Bank_Card_Branch", "支行");
        param.put("Out_Source", outSource);
        String toSign = MD5.toAscii(param);

        param.put("Sign", MD5.md5(toSign + merchantAccount.getPrivateKey()).toUpperCase());
        param.put("Sign_Type", "MD5");
        log.info("AnjiPayer_withdraw_transfer_params:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.ANJI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANJI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/withdrawal.do", param, requestHeader);
        log.info("AnjiPayer_withdraw_transfer_result:{}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(param.toString());
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject retJson = JSONObject.parseObject(resStr);
        if (null == retJson || !retJson.getString("code").equals("00")) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(retJson.getString("msg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> param = new LinkedHashMap<>();
        param.put("Merchant_ID", merchant.getMerchantCode());
        param.put("Type", "WITHDRAWAL");
        param.put("Merchant_Order", orderId);
        String signStrTemp = MD5.toAscii(param);
        param.put("Sign", MD5.md5(signStrTemp + merchant.getPrivateKey()).toUpperCase());
        param.put("Sign_Type", "MD5");
        log.info("AnjiPayer_withdraw_transfer_params:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ANJI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANJI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/remit/query.do", param, requestHeader);
        log.info("AnjiPayer_withdraw_transfer_result:{}", resStr);

        JSONObject retJson = JSON.parseObject(resStr);
        if (null == retJson || !retJson.getString("code").equals("00")) {
            return null;
        }


        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(retJson.getBigDecimal("Amount").divide(BigDecimal.valueOf(100)));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(retJson.getString("Merchant_Order"));
        notify.setThirdOrderId(retJson.getString("Sys_Order"));
        if (retJson.getString("Status").equals("SUCCESS")) {
            notify.setStatus(0);
        } else if (retJson.getString("Status").equals("FAILED")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }

        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> param = new LinkedHashMap<>();
        param.put("Merchant_ID", merchantAccount.getMerchantCode());
        String signStrTemp = MD5.toAscii(param);
        param.put("Sign", MD5.md5(signStrTemp + merchantAccount.getPrivateKey()).toUpperCase());
        param.put("Sign_Type", "MD5");
        log.info("AnjiPayer_QueryBalance_paramMap:{}", JSON.toJSONString(param));
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balance.do", param);
        log.info("AnjiPayer_QueryBalance_resStr:{}", resStr);
        JSONObject retJson = JSON.parseObject(resStr);
        if (null == retJson || !"00".equals(retJson.getString("code"))) {
            return BigDecimal.ZERO;
        }
        JSONObject data = retJson.getJSONObject("data");

        return data.getBigDecimal("total_balance");
    }

    private String getOutSource(GlWithdrawMerchantAccount merchantAccount, BigDecimal amount) throws GlobalException {
        Map<String, String> param = new LinkedHashMap<>();
        param.put("Merchant_ID", merchantAccount.getMerchantCode());
        String signStrTemp = MD5.toAscii(param);
        param.put("Sign", MD5.md5(signStrTemp + merchantAccount.getPrivateKey()).toUpperCase());
        param.put("Sign_Type", "MD5");
        log.info("AnjiPayer_Transfer_QueryBalance_paramMap:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.ANJI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ANJI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/balance.do", param, requestHeader);
        log.info("AnjiPayer_Transfer_QueryBalance_resStr:{}", resStr);
        JSONObject retJson = JSON.parseObject(resStr);
        if (null == retJson) {
            return null;
        }
        JSONArray jsonArray = retJson.getJSONArray("source_details");
        if (null == jsonArray) {
            return null;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if (jsonObject.getBigDecimal("balanceAmount").compareTo(amount) == 1) {
                return jsonObject.getString("paytype");
            }
        }
        return null;
    }

}
