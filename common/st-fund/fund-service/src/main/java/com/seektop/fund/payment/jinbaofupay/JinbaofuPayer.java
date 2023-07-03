package com.seektop.fund.payment.jinbaofupay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service(FundConstant.PaymentChannel.JINBAOFUPAY + "")
public class JinbaofuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    private static final String SERVER_PAY_URL = "/channel/Common/mail_interface";//支付地址

    private static final String SERVER_QUERY_URL = "/channel/Common/query_pay";//查询订单地址

    private static final String SERVER_WITHDRAW_URL = "/channel/Withdraw/mail_interface";//下发地址

    private static final String SERVER_WITHDRAW_QUERY_URL = "/channel/Withdraw/query_pay";//下发查询地址

    private static final String SERVER_MEARCHANT_QUERY_URL = "/channel/Withdraw/query_money";//商户余额查询

    @Resource
    private OkHttpUtil okHttpUtil;
    @Resource
    private GlWithdrawBusiness withdrawService;
    @Resource
    private GlRechargeBusiness rechargeBusiness;
    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankService;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> params = new TreeMap<>();

        params.put("return_type", "json");
        params.put("api_code", account.getMerchantCode());
        params.put("is_type", getType(merchant.getPaymentId()));//支付方式
        params.put("price", req.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "");
        params.put("order_id", req.getOrderId());
        params.put("time", System.currentTimeMillis() / 1000 + "");
        params.put("mark", "CZ");
        params.put("notify_url", account.getNotifyUrl() + merchant.getId());
        params.put("return_url", account.getResultUrl() + merchant.getId());

        String sign = MD5Encoder((MD5.toAscii(params) + "&key=" + account.getPrivateKey())).toUpperCase();
        params.put("sign", sign);
        log.info("JinbaofuPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JINBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINBAOFU_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, params, requestHeader);
        log.info("JINBAOFU_PAY_recharge_prepare_resp:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            throw new GlobalException("创建订单失败");
        }
        if (json != null && StringUtils.isNotEmpty(json.getString("messages"))) {
            JSONObject resultJson = JSON.parseObject(json.getString("messages"));
            if(resultJson != null && resultJson.get("returncode").equals("SUCCESS")){
                result.setRedirectUrl( json.get("payurl").toString());
            }
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JINBAOFU_PAY_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = "";
        if(json != null){
            orderId = json.getString("order_id");
        }
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        GlRecharge recharge = rechargeBusiness.findById(orderId);
        if (recharge == null || recharge.getPaymentId() == null) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api_code", account.getMerchantCode());
        params.put("is_type", getType(recharge.getPaymentId()));//支付方式
        params.put("order_id", orderId);
        params.put("price", recharge.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "");
        String sign = MD5Encoder((MD5.toAscii(params) + "&key=" + account.getPrivateKey())).toUpperCase();
        params.put("sign", sign);
        log.info("JINBAOFU_PAY_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JINBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINBAOFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId.toUpperCase())
                .build();
        String resStr = okHttpUtil.post((account.getPayUrl() + SERVER_QUERY_URL), params, requestHeader);
        log.info("JINBAOFU_PAY_query_resp:{}", resStr);


        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if (json != null && StringUtils.isNotEmpty(json.getString("messages"))) {
            JSONObject resultJson = JSON.parseObject(json.getString("messages"));
            if(resultJson != null && resultJson.get("returncode").equals("SUCCESS")
            && "1".equals(json.getString("code"))){
                // 订单状态判断标准: 0 未处理 1 交易成功 2 支付失败 3 关闭交易 4 支付超时
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(json.getBigDecimal("price"));
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId);
                pay.setThirdOrderId(json.getString("paysapi_id"));
                return pay;
            }
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw withdraw) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api_code", merchantAccount.getMerchantCode());
        params.put("order_id", withdraw.getOrderId());
        params.put("cash_money", withdraw.getAmount().subtract(withdraw.getFee()).setScale(2, BigDecimal.ROUND_DOWN) + "");
        params.put("time", System.currentTimeMillis() + "");
        params.put("bank_code", paymentChannelBankService.getBankCode(withdraw.getBankId(), merchantAccount.getChannelId()));
        params.put("bank_branch", "上海市");
        params.put("bank_account_number", withdraw.getCardNo());
        params.put("bank_compellation", withdraw.getName());
        params.put("t", "0");
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        String sign = MD5Encoder((MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey())).toUpperCase();
        params.put("sign", sign);
        log.info("JINBAOFU_PAY_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.JINBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINBAOFU_PAY.getPaymentName())
                .userId(withdraw.getUserId() + "")
                .userName(withdraw.getUsername())
                .tradeId(withdraw.getOrderId())
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params, requestHeader);
        log.info("JINBAOFU_PAY_doTransfer_resp:{}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(withdraw.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        String returnMessage = "";
        if (json != null && StringUtils.isNotEmpty(json.getString("messages"))) {
            JSONObject resultJson = JSON.parseObject(json.getString("messages"));
            returnMessage = resultJson.getString("return_msg");
            if (!resultJson.get("returncode").equals("SUCCESS")) {
                result.setValid(false);
                result.setMessage(returnMessage);
                return result;
            }
        }
        result.setValid(true);
        result.setMessage(returnMessage);
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("JINBAOFU_PAY_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("orderno");
        } else {
            orderId = json.getString("orderno");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        GlWithdraw withdraw = withdrawService.findById(orderId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api_code", merchant.getMerchantCode());
        params.put("order_id", orderId);
        params.put("cash_money", withdraw.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "");
        String sign = MD5Encoder((MD5.toAscii(params) + "&key=" + merchant.getPrivateKey())).toUpperCase();
        params.put("sign", sign);
        log.info("JINBAOFU_PAY_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JINBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINBAOFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();

        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_WITHDRAW_QUERY_URL, params, requestHeader);
        log.info("JINBAOFU_PAY_doTransferQuery_resp:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        JSONObject json = JSON.parseObject(resStr);
        if (json != null && StringUtils.isNotEmpty(json.getString("messages"))) {
            JSONObject resultJson = JSON.parseObject(json.getString("messages"));
            if (resultJson.get("return_code").equals("SUCCESS")) {
                notify.setAmount(json.getBigDecimal("cash_money"));
                notify.setMerchantCode(merchant.getMerchantCode());
                notify.setMerchantId(merchant.getMerchantId());
                notify.setMerchantName(merchant.getChannelName());
                notify.setOrderId(json.getString("order_id"));
                notify.setThirdOrderId(json.getString("paysapi_id"));
                if (json.getString("code").equals("1")) {//订单状态判断标准： 0 未处理 1 交易成功 2 支付失败 3 关闭交易 4 支付超时  商户返回出款状态：0成功，1失败,2处理中
                    notify.setStatus(0);
                } else if (json.getString("code").equals("2")) {
                    notify.setStatus(1);
                } else {
                    notify.setStatus(2);
                }
            }
        }
        log.info("notify:{}", JSON.toJSONString(notify));
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api_code", merchantAccount.getMerchantCode());
        String sign = MD5Encoder((MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey())).toUpperCase();
        params.put("sign", sign);
        log.info("JINBAOFU_PAY_queryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.JINBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JINBAOFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_MEARCHANT_QUERY_URL, params, requestHeader);
        log.info("JINBAOFU_PAY_queryBalance_resp:{}", resStr);
        if (StringUtils.isNotEmpty(resStr)) {
            JSONObject json = JSON.parseObject(resStr);
            if (json != null && StringUtils.isNotEmpty(json.getString("messages"))) {
                JSONObject resultJson = JSON.parseObject(json.getString("messages"));
                if (resultJson.get("return_code").equals("SUCCESS")) {
                    return json.getBigDecimal("money");
                }
            }
        }
        return null;
    }


    /**
     * 获取支付方式
     *
     * @param paymentId
     * @return
     */
    private String getType(Integer paymentId) {
        String result = null;
        if (paymentId == FundConstant.PaymentType.ALI_PAY) {
            result = "alipaygp";//支付宝
        } else if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            result = "bank";//卡卡转帐
        }else if (paymentId == FundConstant.PaymentType.ALI_TRANSFER){
            result = "ali2bank";//支付宝转帐
        }
        return result;
    }

    public String MD5Encoder(String s) {
        try {
            byte[] btInput = s.getBytes("utf-8");
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            mdInst.update(btInput);
            byte[] md = mdInst.digest();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < md.length; i++) {
                int val = ((int) md[i]) & 0xff;
                if (val < 16) {
                    sb.append("0");
                }
                sb.append(Integer.toHexString(val));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
