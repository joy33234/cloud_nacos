package com.seektop.fund.payment.juhepay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ServiceException;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.JUHEPAY + "")
public class JuhePayer implements GlPaymentRechargeHandler, GlRechargeCancelHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Autowired
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Autowired
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    private static final String SERVER_PAY_URL = "/api/pay";

    private static final String SERVER_QUERY_URL = "/api/query";

    private static final String SERVER_CLOSE_URL = "/api/close";

    private static final String SERVER_FORCE_SUCCESS = "/api/mchVerify";

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("merchant_code", account.getMerchantCode());
        contentMap.put("merchant_order_no", req.getOrderId());
        contentMap.put("pay_type", merchant.getPaymentId() + "");
        contentMap.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        contentMap.put("notify_url", account.getNotifyUrl() + merchant.getId());
        contentMap.put("return_url", account.getResultUrl() + merchant.getId());
        contentMap.put("user_level", req.getUserLevel());
        contentMap.put("pay_info", req.getUsername());
        //下单请求来源设备，1:APP 2:PC
        if (req.getClientType() == ProjectConstant.ClientType.PC) {
            contentMap.put("terminal_type", "1");
        } else {
            contentMap.put("terminal_type", "0");
        }
        Integer isManual = 2;
        if (StringUtils.isNotEmpty(req.getOriginalOrderId())) {
            isManual = 1;
            contentMap.put("source_merchant_order_no", req.getOriginalOrderId());
        }
        contentMap.put("is_manual", isManual.toString());
        Map<String, String> params = new HashMap<>();
        params.put("merchant_code", account.getMerchantCode());
        try {
            log.info("JuhePayer_recharge_prepare_params:{}", JSONObject.toJSONString(contentMap));
            String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(JSONObject.toJSONString(contentMap).getBytes(StandardCharsets.UTF_8), account.getPublicKey()));
            params.put("sign", encrypted);
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE.getCode())
                    .channelId(PaymentMerchantEnum.JUHE_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.JUHE_PAY.getPaymentName())
                    .userId(req.getUserId() + "")
                    .userName(req.getUsername())
                    .tradeId(req.getOrderId())
                    .build();
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, params, requestHeader);
            log.info("JuhePayer_recharge_prepare_resp:{}", resStr);

            JSONObject json = JSONObject.parseObject(resStr);
            if (json == null || !json.getString("code").equals("1")) {
                throw new ServiceException("创建订单失败");
            }

            json = json.getJSONObject("data");
            if (json == null) {
                throw new ServiceException("创建订单失败");
            }
            if (json.getString("type").equals("1")) {
                result.setRedirectUrl(json.getString("data"));
            } else if (json.getString("type").equals("2")) {
                result.setMessage(json.getString("data"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("uhePayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        try {
            String decrypted = new String(RSAUtils.decryptByPublicKey(Base64.decodeBase64(json.getString("sign").getBytes()), account.getPublicKey()));
            json = JSONObject.parseObject(decrypted);
            String orderId = json.getString("merchant_order_no");
            if (null != orderId && !"".equals(orderId)) {
                return this.query(account, orderId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("merchant_code", account.getMerchantCode());
        contentMap.put("merchant_order_no", orderId);
        try {
            Map<String, String> params = new HashMap<>();
            params.put("merchant_code", account.getMerchantCode());
            String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(JSONObject.toJSONString(contentMap)
                    .getBytes(StandardCharsets.UTF_8), account.getPublicKey()));
            params.put("sign", encrypted);
            log.info("JuhePayer_query_params:{}", JSONObject.toJSONString(contentMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.JUHE_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.JUHE_PAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId)
                    .build();
            String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_QUERY_URL, JSONObject.toJSONString(params), requestHeader);
            log.info("JuhePayer_query_resp:{}", resStr);
            if (StringUtils.isEmpty(resStr)) return null;
            JSONObject json = JSONObject.parseObject(resStr);
            JSONObject data = json.getJSONObject("data");
            if ("1".equals(json.getString("code")) && "1".equals(data.getString("platform_order_status"))) {
                RechargeNotify pay = new RechargeNotify();
                pay.setOrderId(data.getString("merchant_order_no"));
                pay.setAmount(data.getBigDecimal("real_amount"));
                pay.setFee(BigDecimal.ZERO);
                pay.setThirdOrderId(data.getString("platform_order_no"));
                return pay;
            }

        } catch (Exception e) {
            log.error("JuhePayer_query_error:{}", e.getMessage());
            throw new GlobalException(e.getMessage(), e);
        }
        return null;
    }

    public void cancel(GlPaymentMerchantaccount payment, GlRecharge req) {
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("merchant_code", payment.getMerchantCode());
        contentMap.put("merchant_order_no", req.getOrderId());
        try {
            Map<String, String> params = new HashMap<>();
            params.put("merchant_code", payment.getMerchantCode());
            String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(JSONObject.toJSONString(contentMap)
                    .getBytes(StandardCharsets.UTF_8), payment.getPublicKey()));
            params.put("sign", encrypted);
            log.info("JuhePayer_cancel_params:{}", JSONObject.toJSONString(contentMap));
            String resStr = okHttpUtil.postJSON(payment.getPayUrl() + SERVER_CLOSE_URL, JSONObject.toJSONString(params));
            log.info("XingPayer_cancel_resp:{}", resStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> contentMap = new HashMap<>();
        Map<String, String> params = new HashMap<>();
        WithdrawResult result = new WithdrawResult();
        try {
            contentMap.put("merchant_code", merchantAccount.getMerchantCode());
            contentMap.put("merchant_order_no", req.getOrderId());
            contentMap.put("pay_type", "bank");
            contentMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
            contentMap.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

            GlFundUserlevel userlevel = glFundUserlevelBusiness.findById(req.getUserLevel());
            if (userlevel == null || StringUtils.isEmpty(userlevel.getName())) {
                throw new ServiceException("用户层级异常");
            }
            contentMap.put("user_level", userlevel.getName());
            contentMap.put("bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
            contentMap.put("bank_card_no", req.getCardNo());
            contentMap.put("bank_card_holder", req.getName());
            contentMap.put("bank_card_phone", "13611111111");
            contentMap.put("limit_type", "1");//额度类型 1普通
            contentMap.put("channel_use", "1");//可用渠道 1用户提现


            params.put("merchant_code", merchantAccount.getMerchantCode());
            String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(JSONObject.toJSONString(contentMap).getBytes(StandardCharsets.UTF_8), merchantAccount.getPublicKey()));
            params.put("sign", encrypted);
            log.info("JuhePayer_Transfer_params: {}", JSON.toJSONString(contentMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW.getCode())
                    .channelId(PaymentMerchantEnum.JUHE_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.JUHE_PAY.getPaymentName())
                    .userId(req.getUserId() + "")
                    .userName(req.getUsername())
                    .tradeId(req.getOrderId())
                    .build();
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", params, requestHeader);
            log.info("JuhePayer_Transfer_resStr: {}", resStr);

            result.setOrderId(req.getOrderId());
            result.setReqData(JSON.toJSONString(params));
            result.setResData(resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !"1".equals(json.getString("code"))) {
                result.setValid(false);
                result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"));
                return result;
            }
            result.setValid(true);
            result.setMessage(json.getString("msg"));

        } catch (Exception e) {
            log.error("JuhePayer_doTransfer_error:{}", e.getMessage());
            throw new GlobalException(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("JuhePayer_transfer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        try {
            String decrypted = new String(RSAUtils.decryptByPublicKey(Base64.decodeBase64(json.getString("sign").getBytes()), merchant.getPublicKey()));
            json = JSONObject.parseObject(decrypted);
            String orderId = json.getString("merchant_order_no");
            if (StringUtils.isNotEmpty(orderId)) {
                return doTransferQuery(merchant, orderId);
            }
        } catch (Exception e) {
            log.error("JuhePayer_doTransferNotify_error:{}", e.getMessage());
            throw new GlobalException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        WithdrawNotify notify = new WithdrawNotify();
        try {
            Map<String, String> contentMap = new HashMap<>();
            contentMap.put("merchant_code", merchant.getMerchantCode());
            contentMap.put("merchant_order_no", orderId);
            Map<String, String> params = new HashMap<>();
            params.put("merchant_code", merchant.getMerchantCode());
            String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(JSONObject.toJSONString(contentMap)
                    .getBytes(StandardCharsets.UTF_8), merchant.getPublicKey()));
            params.put("sign", encrypted);

            log.info("JuhePayer_TransferQuery_reqMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.JUHE_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.JUHE_PAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId)
                    .build();
            String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/queryWithdraw", JSON.toJSONString(params), requestHeader);
            log.info("JuhePayer_TransferQuery_resStr:{}", resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !json.getString("code").equals("1")) {
                return null;
            }
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                notify.setAmount(data.getBigDecimal("merchant_amount").setScale(2, RoundingMode.DOWN));
                notify.setMerchantCode(merchant.getMerchantCode());
                notify.setMerchantId(merchant.getMerchantId());
                notify.setMerchantName(merchant.getChannelName());
                notify.setOrderId(data.getString("merchant_order_no"));
                notify.setThirdOrderId(data.getString("platform_order_no"));
                if (data.getString("platform_order_status").equals("6")) {//商户返回出款状态：0成功，1失败,2处理中   聚合订单状态1 下单中 2下单失败 3自动出款中 4 超时关闭 5出款失败 6出款成功
                    notify.setStatus(0);
                } else if (data.getString("platform_order_status").equals("5") || data.getString("platform_order_status").equals("4")) {
                    notify.setStatus(1);
                } else {
                    notify.setStatus(2);
                }
            }
        } catch (Exception e) {
            log.error("JuhePayer_doTransferQuery_error:{}", e.getMessage());
            throw new GlobalException(e.getMessage(), e);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return BigDecimal.ZERO;
    }
}
