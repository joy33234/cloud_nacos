package com.seektop.fund.payment.beileipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
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


@Slf4j
@Service(FundConstant.PaymentChannel.BEILEIPAY + "")
public class BeileiPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {


    private static final String SERVER_PAY_URL = "/pay/gateway/unify.do";

    private static final String SERVER_QUERY_URL = "/pay/gateway/query.do";

    private static final String SERVER_DF_PAY_URL = "/pay/gateway/withdraw.do";

    private static final String SERVER_DF_QUERY_URL = "/pay/gateway/withdrawQuery.do";

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
        String payType;
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "3003";
            } else {
                payType = "3013";
            }
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            payType = "2023";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            payType = "1023";
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            payType = "3010";//支付宝转帐，  根据商户密钥 确定是支付宝/支付宝转帐
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "3008";//卡卡
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            payType = "3012";//云闪付转卡
        } else {
            payType = "3007";
        }
        prepareToScan(merchant, account, req, result, payType);

    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        String arr[] = account.getMerchantCode().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户未配置机构号-创建订单失败");
        }
        Map<String, String> params = new HashMap<>();
        params.put("TRDE_CODE", "20001");
        params.put("PRT_CODE", arr[1]);
        params.put("VER_NO", "1.0");
        params.put("MERC_ID", arr[0]);
        params.put("BIZ_CODE", payType);
        params.put("ORDER_NO", req.getOrderId());
        params.put("TXN_AMT", req.getAmount().toString());
        params.put("PRO_DESC", "Recharge");
        params.put("NOTIFY_URL", account.getNotifyUrl() + merchant.getId());
        params.put("NON_STR", System.currentTimeMillis() + "");
        params.put("TM_SMP", System.currentTimeMillis() + "");
        params.put("SIGN_TYP", "MD5");
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            params.put("BNK_CD", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
            params.put("CLIENT_IP", req.getIp());
        }
        String toSign = MD5.toAscii(params);
        toSign += "&KEY=" + account.getPrivateKey();
        params.put("SIGN_DAT", MD5.md5(toSign));
        log.info("BeileiPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.BEILEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.BEILEI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_PAY_URL, JSON.toJSONString(params), requestHeader);
        log.info("BeileiPayer_recharge_prepare_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject json = JSONObject.parseObject(resStr);
        if (json == null || !"0000".equals(json.getString("RETURNCODE")) || !json.getString("RETURNCON").equals("SUCCESS")) {
            throw new RuntimeException("创建订单失败");
        }
        String url = json.getString("QR_CODE");
        if (StringUtils.isNotEmpty(url)) {
            result.setRedirectUrl(url);
        } else {
            result.setMessage(json.getString("RET_HTML"));
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("BeileiPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("ORDER_NO");
        } else {
            orderId = json.getString("ORDER_NO");
        }
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String arr[] = account.getMerchantCode().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new GlobalException("商户未配置机构号-创建订单失败");
        }

        Map<String, String> params = new HashMap<>();
        params.put("TRDE_CODE", "20002");
        params.put("PRT_CODE", arr[1]);
        params.put("VER_NO", "1.0");
        params.put("MERC_ID", arr[0]);
        params.put("ORDER_NO", orderId);
        params.put("NON_STR", System.currentTimeMillis() + "");
        params.put("TM_SMP", System.currentTimeMillis() + "");
        params.put("SIGN_TYP", "MD5");
        String toSign = MD5.toAscii(params);
        toSign += "&KEY=" + account.getPrivateKey();
        params.put("SIGN_DAT", MD5.md5(toSign));
        log.info("BeileiPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.BEILEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.BEILEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId.toUpperCase())
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + SERVER_QUERY_URL, JSON.toJSONString(params), requestHeader);
        log.info("BeileiPayer_query_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        // 请求成功 并且 支付成功
        if ("0000".equals(json.getString("RETURNCODE")) && "1".equals(json.getString("ORD_STS"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("TXN_AMT").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("OUT_TRADE_NO"));
            return pay;
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String arr[] = merchantAccount.getMerchantCode().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户未配置机构号-创建订单失败");
        }
        Map<String, String> params = new HashMap<>();
        params.put("TRDE_CODE", "20003");
        params.put("PRT_CODE", arr[1]);
        params.put("VER_NO", "1.0");
        params.put("MERC_ID", arr[0]);
        params.put("OUT_WITHDRAW_NO", req.getOrderId());
        params.put("WITHDRAW_MONEY", req.getAmount().subtract(req.getFee()).toString());
        params.put("PERSON_NM", req.getName());
        params.put("CARD_NO", req.getCardNo());
        params.put("BNK_NM", req.getBankName());
        params.put("BNK_NO", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("BNK_CD", "0000");
        params.put("CRP_ID_NO", "120103200101017417");
        params.put("PHONE_NO", "13611111148");
        params.put("PAY_ID", "D0");
        params.put("NON_STR", System.currentTimeMillis() + "");
        params.put("TM_SMP", System.currentTimeMillis() + "");
        params.put("NOTIFY_URL", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("SIGN_TYP", "MD5");
        String toSign = MD5.toAscii(params);
        toSign += "&KEY=" + merchantAccount.getPrivateKey();
        params.put("SIGN_DAT", MD5.md5(toSign));
        log.info("BeileiPayer_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.BEILEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.BEILEI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_DF_PAY_URL, JSON.toJSONString(params), requestHeader);
        log.info("BeileiPayer_doTransfer_resp:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        if (!"0000".equals(json.getString("RETURNCODE"))) {
            result.setValid(false);
            result.setMessage(json.getString("RETURNCON"));
            return result;
        }
        req.setMerchantId(merchantAccount.getMerchantId());
        result.setValid(true);
        result.setMessage(json.getString("RETURNCON"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("BeileiPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("TAMT_ORD_NO");
        } else {
            orderId = json.getString("TAMT_ORD_NO");
        }
        if (null != orderId && !"".equals(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String arr[] = merchant.getMerchantCode().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户未配置机构号-创建订单失败");
        }
        Map<String, String> params = new HashMap<>();
        params.put("TRDE_CODE", "20004");
        params.put("PRT_CODE", arr[1]);
        params.put("VER_NO", "1.0");
        params.put("MERC_ID", arr[0]);
        params.put("OUT_WITHDRAW_NO", orderId);
        params.put("NON_STR", System.currentTimeMillis() + "");
        params.put("TM_SMP", System.currentTimeMillis() + "");
        params.put("SIGN_TYP", "MD5");
        String toSign = MD5.toAscii(params);
        toSign += "&KEY=" + merchant.getPrivateKey();
        params.put("SIGN_DAT", MD5.md5(toSign));
        log.info("BeileiPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.BEILEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.BEILEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + SERVER_DF_QUERY_URL, JSONObject.toJSONString(params), requestHeader);
        log.info("BeileiPayer_doTransferQuery_resp:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (!"0000".equals(json.getString("RETURNCODE"))) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("WITHDRAW_MONEY"));
//        notify.setMerchantCode(arr[0]);
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(json.getString("OUT_WITHDRAW_NO"));
        notify.setThirdOrderId("");
        if (json.getString("BUS_STS").equals("00")) {
            notify.setStatus(0);
        } else if (json.getString("BUS_STS").equals("70")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String arr[] = merchantAccount.getMerchantCode().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户未配置机构号-创建订单失败");
        }
        Map<String, String> params = new HashMap<>();
        params.put("TRDE_CODE", "20005");
        params.put("PRT_CODE", arr[1]);
        params.put("VER_NO", "1.0");
        params.put("MERC_ID", arr[0]);
        params.put("NON_STR", System.currentTimeMillis() + "");
        params.put("TM_SMP", System.currentTimeMillis() + "");
        params.put("SIGN_TYP", "MD5");
        String toSign = MD5.toAscii(params);
        toSign += "&KEY=" + merchantAccount.getPrivateKey();
        params.put("SIGN_DAT", MD5.md5(toSign));
        log.info("BeileiPayer_queryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.BEILEI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.BEILEI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + SERVER_QUERY_URL, JSONObject.toJSONString(params), requestHeader);
        log.info("BeileiPayer_queryBalance_resp:{}", resStr);
        JSONObject json = JSONObject.parseObject(resStr);
        if ("0000".equals(json.getString("RETURNCODE"))) {
            return json.getBigDecimal("D_AMT");
        }
        return BigDecimal.ZERO;
    }
}
