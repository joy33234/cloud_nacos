package com.seektop.fund.payment.aibeipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.AIBEI + "")
public class AibeiPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    public Map<Integer, String> bankIdCodeMap = new HashMap<Integer, String>() {{
        put(1, "CMB");//招商银行
        put(2, "ICBC");//中国工商银行
        put(3, "CCB");//中国建设银行
        put(4, "ABC");//中国农业银行
        put(5, "BOC");//中国银行
        put(6, "COMM");//交通银行
        put(7, "GDB");//广东发展银行
        put(8, "CEBB");//中国光大银行
        put(9, "SPDB");//上海蒲东发展银行
        put(10, "CMBC");//中国民生银行
        put(11, "SPAB");//平安银行
        put(12, "CIB");//兴业银行
        put(13, "CITIC");//中信银行
        put(14, "PSBC");//中国邮政储蓄银行
    }};

    public Map<Integer, String> typeMap = new HashMap<Integer, String>() {{
        put(FundConstant.PaymentType.UNIONPAY_SACN, "unionpay");//银联
        put(FundConstant.PaymentType.ALI_PAY, "alipaygp");
        put(FundConstant.PaymentType.WECHAT_PAY, "wechat");
        put(FundConstant.PaymentType.BANKCARD_TRANSFER, "bank");
        put(FundConstant.PaymentType.ALI_TRANSFER, "ali2bank");
    }};

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result);
        }
    }

    //银联扫码支付
    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = account.getPrivateKey();// 私钥
        param.put("return_type", "json");//商户号
        param.put("api_code", account.getMerchantCode());
        param.put("is_type", typeMap.get(merchant.getPaymentId()));
        param.put("price", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        param.put("order_id", req.getOrderId());
        long timeStamp = System.currentTimeMillis() / 1000;
        param.put("time", String.valueOf(timeStamp));//时间戳
        param.put("mark", "CZ");
        param.put("return_url", account.getResultUrl() + merchant.getId());
        param.put("notify_url", account.getNotifyUrl() + merchant.getId());
        if(FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()){
            param.put("bank_code", req.getFromCardUserName());
        }
        String signStrTemp = MD5.toAscii(param) + "&key=" + keyValue;
        param.put("sign", MD5.md5(signStrTemp).toUpperCase());
        log.info("AibeiPayer_recharge_prepare_params:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String retBack = okHttpUtil.post((account.getPayUrl() + "/channel/Common/mail_interface"), param, requestHeader);
        log.info("AibeiPayer_recharge_prepare_result:{}", retBack);

        JSONObject json = JSON.parseObject(retBack);
        if (json == null) {
            throw new RuntimeException("创建订单失败");
        }

        JSONObject message = json.getJSONObject("messages");
        if (message.getString("returncode").equals("SUCCESS")) {
            result.setRedirectUrl(json.getString("payurl"));
        } else {
            result.setErrorCode(1);
            result.setErrorMsg(message.getString("returnmsg"));
        }
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("AibeiPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String dataStr = resMap.get("reqBody");
        log.info("AibeiPayer_Notify_dataStr:{}", dataStr);
        JSONObject dataJson = JSON.parseObject(dataStr);
        if (null == dataJson) {
            return null;
        }
        String code = dataJson.getString("code");
        String orderId = dataJson.getString("order_id");
        String price = dataJson.getString("price");
        String isType = dataJson.getString("is_type");
        StringBuilder orderStr = new StringBuilder();
        orderStr.append(orderId).append("-").append(price).append("-").append(isType);
        if ("1".equals(code)) {
            return query(account, orderStr.toString());
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        log.info("AibeiPayer_query_orderId: {}", orderId);
        String keyValue = account.getPrivateKey();
        String[] dataStr = orderId.split("-");
        Map<String, String> param = new HashMap<>();
        String price = new BigDecimal(dataStr[1]).setScale(2, RoundingMode.DOWN).toString();
        param.put("api_code", account.getMerchantCode());
        param.put("is_type", dataStr[2]);
        param.put("order_id", dataStr[0]);
        param.put("price", price);
        String signStr = MD5.toAscii(param) + "&key=" + keyValue;
        param.put("sign", MD5.md5(signStr).toUpperCase());
        log.info("AibeiPayer_query_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/channel/Common/query_pay", param, requestHeader);
        log.info("AibeiPayer_query_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);

        if (json == null) {
            return null;
        }

        String code = json.getString("code");
        if ("1".equals(code)) {
            RechargeNotify notify = new RechargeNotify();
            notify.setAmount(json.getBigDecimal("price").setScale(2, RoundingMode.DOWN));
            notify.setFee(BigDecimal.ZERO);
            notify.setOrderId(dataStr[0]);
            notify.setThirdOrderId(json.getString("paysapi_id"));
            return notify;
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = merchantAccount.getPrivateKey();// 私钥

        param.put("api_code", merchantAccount.getMerchantCode());
        param.put("order_id", req.getOrderId());
        param.put("cash_money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        long timeStamp = System.currentTimeMillis() / 1000;
        param.put("time", String.valueOf(timeStamp));
        param.put("bank_code", bankIdCodeMap.get(req.getBankId()));
        param.put("bank_branch", "上海市");
        param.put("bank_account_number", req.getCardNo());
        param.put("bank_compellation", req.getName());
        param.put("t", "0");
        param.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

        String signStr = MD5.toAscii(param) + "&key=" + keyValue;
        log.info("AibeiPayer_Transfer_toSign:{}", signStr);
        param.put("sign", MD5.md5(signStr).toUpperCase());

        log.info("AibeiPayer_withdraw_transfer_params:{}", JSON.toJSONString(param));

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/channel/Withdraw/mail_interface", param, requestHeader);
        log.info("AibeiPayer_withdraw_transfer_result:{}", retBack);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(param.toString());
        result.setResData(retBack);
        JSONObject retJson = JSONObject.parseObject(retBack);
        if (null == retJson || StringUtils.isEmpty(retJson.getString("return_code"))) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        if (!"SUCCESS".equals(retJson.getString("return_code"))) {
            result.setValid(false);
            result.setMessage(retJson.getString("return_msg"));
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(retJson.getString("return_msg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("AibeiPayer_doTransferNotify_resMap:{}", JSON.toJSONString(resMap));
        String dataStr = resMap.get("reqBody");

        JSONObject dataJson = JSON.parseObject(dataStr);
        if (null == dataJson) {
            return null;
        }
        String orderId = dataJson.getString("order_id");
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return doTransferQuery(merchant, orderId);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {

        String keyValue = merchant.getPrivateKey();
        Map<String, String> param = new HashMap<>();
        GlWithdraw withdraw = glWithdrawBusiness.findById(orderId);
        String price = withdraw.getAmount().setScale(2, RoundingMode.DOWN).toString();
        param.put("api_code", merchant.getMerchantCode());
        param.put("order_id", orderId);
        param.put("cash_money", price);
        String signStr = MD5.toAscii(param) + "&key=" + keyValue;
        log.info("AibeiPayer_TransferQuery_toSign:{}", signStr);
        param.put("sign", MD5.md5(signStr).toUpperCase());
        log.info("AibeiPayer_doTransferQuery_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String retBack = okHttpUtil.post(merchant.getPayUrl() + "/channel/Withdraw/query_pay", param, requestHeader);
        log.info("AibeiPayer_doTransferQuery_resStr: {}", retBack);
        JSONObject retJson = JSON.parseObject(retBack);
        if (null == retJson) {
            return null;
        }

        String message = retJson.getString("messages");
        JSONObject messageJson = JSONObject.parseObject(message);
        if (null == messageJson || !"SUCCESS".equals(messageJson.getString("return_code"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(retJson.getBigDecimal("cash_money"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setThirdOrderId(retJson.getString("paysapi_id"));
        if ("1".equals(retJson.getString("code"))) {
            notify.setStatus(0);
        } else if ("2".equals(retJson.getString("code"))) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String keyValue = merchantAccount.getPrivateKey();
        Map<String, String> param = new HashMap<>();
        param.put("api_code", merchantAccount.getMerchantCode());
        String signStr = MD5.toAscii(param) + "&key=" + keyValue;
        log.info("AibeiPayer_Query_toSign:{}", signStr);
        param.put("sign", MD5.md5(signStr).toUpperCase());
        log.info("AibeiPayer_queryBalance_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.AIBEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.AIBEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/channel/Withdraw/query_money", param, requestHeader);
        log.info("AibeiPayer_queryBalance_resStr: {}", retBack);
        JSONObject retJson = JSON.parseObject(retBack);
        if (null == retJson || StringUtils.isEmpty(retJson.getString("money"))) {
            return BigDecimal.ZERO;
        }
        return retJson.getBigDecimal("money");
    }
}
