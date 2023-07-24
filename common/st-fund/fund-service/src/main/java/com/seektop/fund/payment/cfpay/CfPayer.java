package com.seektop.fund.payment.cfpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cfpay 接口
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.CFPAY + "")
public class CfPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            params.put("out_trade_no", req.getOrderId());
            params.put("notify_url", merchantaccount.getNotifyUrl() + merchant.getId());
            params.put("paid_name", req.getFromCardUserName());

            Map<String, String> headParams = new HashMap<String, String>();
            headParams.put("Authorization", "Bearer " + merchantaccount.getPrivateKey());
            headParams.put("Accept", "application/json");
            headParams.put("content-type", "application/json");

            log.info("CfPayer_Prepare_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String resStr = okHttpUtil.postJSON(merchantaccount.getPayUrl() + "/api/transaction", JSON.toJSONString(params), headParams, requestHeader);
            log.info("CfPayer_Prepare_resStr:{}", resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null) {
                throw new GlobalException("创建订单失败");
            }
            String redirectUrl = json.getString("uri");
            String orderId = json.getString("out_trade_no");
            if (StringUtils.isEmpty(redirectUrl) || StringUtils.isEmpty(orderId) || !orderId.equals(req.getOrderId())) {
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(redirectUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("CfPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String thirdOrderId = json.getString("trade_no");
        String orderId = json.getString("out_trade_no");
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        log.info("CfPayer_query_params_orderId:{}_thirdOrderId:{}", orderId);

        Map<String, String> headParams = new HashMap<String, String>();
        headParams.put("Authorization", "Bearer " + account.getPrivateKey());
        headParams.put("Accept", "application/json");

        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());

        String resStr = okHttpUtil.get(account.getPayUrl() + "/api/transaction/" + orderId, null, requestHeader, headParams);
        log.info("CfPayer_query_resStr:{}", resStr);


        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        // 订单状态判断标准:  success => 成功  progress => 进行中 timeout => 逾时
        if (json.getString("status").equals("success")) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("trade_no"));
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
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {

        Map<String, Object> params = null;
        String resStr = null;
        try {
            params = new LinkedHashMap<String, Object>();
            params.put("application_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
            params.put("bank", req.getBankName());
            params.put("bank_branch", "上海市");
            params.put("card_sn", req.getCardNo());
            params.put("out_trade_no", req.getOrderId());
            params.put("owner", req.getName());

            params.put("sign", getSign(MerchantJSON.encode(params), merchantAccount.getPrivateKey()));

            Map<String, String> headParams = new HashMap<String, String>();
            headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey());
            headParams.put("Accept", "application/json");
            headParams.put("content-type", "application/json");


            log.info("CfPayer_doTransfer_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
            resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/withdraw", params, requestHeader, headParams);

            log.info("CfPayer_doTransfer_resStr:{}", resStr);
        } catch (Exception e) {
            e.printStackTrace();
        }

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"200".equals(json.getString("code"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("message"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("CfPayer_transfer_notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("out_trade_no");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {

        Map<String, String> headParams = new HashMap<String, String>();
        headParams.put("Authorization", "Bearer " + merchant.getPrivateKey());
        headParams.put("Accept", "application/json");
        headParams.put("content-type", "application/json");


        log.info("CfPayer_TransferQuery_reqMap");
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/withdraw/" + orderId, null, requestHeader, headParams);
        log.info("CfPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !json.getString("code").equals("200")) {
            return null;
        }
        JSONObject dataJSON = json.getJSONObject("result");
        WithdrawNotify notify = new WithdrawNotify();
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("application_amount"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(dataJSON.getString("out_trade_no"));
            notify.setThirdOrderId("");
            if (dataJSON.getString("status").equals("success")) {//订单状态progress=>申请中 withdrawing => 下发中 success => 成功 failed => 退回    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0);
            } else if (dataJSON.getString("status").equals("failed")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {

        Map<String, String> headParams = new HashMap<String, String>();
        headParams.put("Authorization", "Bearer " + merchantAccount.getPrivateKey());
        headParams.put("Accept", "application/json");

        log.info("CfPayer_queryBalance_headParams");
        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/withdraw/balance", null, requestHeader, headParams);
        log.info("CfPayer_queryBalance_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);


        if (json != null && StringUtils.isNotEmpty(json.getString("balance"))) {
            String balanceStr = json.getString("balance").replaceAll(",", "");
            return new BigDecimal(balanceStr);
        }
        return BigDecimal.ZERO;
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws UnsupportedEncodingException
     */
    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder();
        Mac sha256 = Mac.getInstance("HmacSHA256");
        sha256.init(new SecretKeySpec(accessToken.getBytes("UTF8"), "HmacSHA256"));

        return encoder.encodeToString(sha256.doFinal(value.getBytes("UTF8")));
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
                .channelId(PaymentMerchantEnum.CF_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.CF_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
