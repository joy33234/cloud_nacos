package com.seektop.fund.payment.xinpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 新付支付
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.XINPAY + "")
public class XinPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String version = "V1";
        String mer_no = payment.getMerchantCode(); // 商户编号
        String mer_order_no = req.getOrderId(); // 商户订单号
        String ccy_no = "CNY"; // 交易币种
        String order_amount = String.valueOf(req.getAmount().multiply(BigDecimal.valueOf(100)).intValue()); // 支付金额
        String busi_code = "100401";
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            busi_code = "100501";
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            busi_code = "100201";
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            busi_code = "100601";
        } else if (FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            busi_code = "100801";
        }
        String goods = "CZ";
        String bg_url = payment.getNotifyUrl() + merchant.getId(); // 商户接收支付成功数据的地址
        String page_url = payment.getResultUrl() + merchant.getId();

        StringBuilder toSign = new StringBuilder();
        toSign.append("bg_url=").append(bg_url).append("&");
        toSign.append("busi_code=").append(busi_code).append("&");
        toSign.append("ccy_no=").append(ccy_no).append("&");
        toSign.append("goods=").append(goods).append("&");
        toSign.append("mer_no=").append(mer_no).append("&");
        toSign.append("mer_order_no=").append(mer_order_no).append("&");
        toSign.append("order_amount=").append(order_amount).append("&");
        if (FundConstant.PaymentType.UNIONPAY_SACN != merchant.getPaymentId()
                && FundConstant.PaymentType.ALI_PAY != merchant.getPaymentId()
                && FundConstant.PaymentType.JD_PAY != merchant.getPaymentId()
                && FundConstant.PaymentType.QQ_PAY != merchant.getPaymentId()) {
            toSign.append("page_url=").append(page_url).append("&");
        }
        toSign.append("version=").append(version).append("&");
        toSign.append("key=").append(keyValue);
        log.info("XinPayer_prepare_toSign: {}", toSign.toString());
        String sign = MD5.md5(toSign.toString());

        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.QQ_PAY == merchant.getPaymentId()) {
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("version", version);
            jsonParam.put("mer_no", mer_no);
            jsonParam.put("mer_order_no", mer_order_no);
            jsonParam.put("ccy_no", ccy_no);
            jsonParam.put("order_amount", order_amount);
            jsonParam.put("busi_code", busi_code);
            jsonParam.put("goods", goods);
            jsonParam.put("bg_url", bg_url);
            jsonParam.put("sign", sign);
            log.info("XinPayer_prepare_jsonParam: {}", jsonParam.toJSONString());
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE.getCode())
                    .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                    .userId(req.getUserId() + "")
                    .userName(req.getUsername())
                    .tradeId(req.getOrderId())
                    .build();

            String resStr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/orderPay", jsonParam.toString(), requestHeader);
            log.info("XinPayer_resStr: {}", resStr);
            if (StringUtils.isEmpty(resStr)) {
                throw new RuntimeException("创建订单失败");
            }
            JSONObject json = JSON.parseObject(resStr);

            String code_url = json.getString("code_url");
            if (StringUtils.isEmpty(code_url)) {
                throw new RuntimeException("创建订单失败");
            }
            result.setMessage(HtmlTemplateUtils.getQRCode(new String(Base64.decodeBase64(code_url))));
        } else {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("version", version);
            paramMap.put("mer_no", mer_no);
            paramMap.put("mer_order_no", mer_order_no);
            paramMap.put("ccy_no", ccy_no);
            paramMap.put("order_amount", order_amount);
            paramMap.put("busi_code", busi_code);
            paramMap.put("goods", goods);
            paramMap.put("bg_url", bg_url);
            paramMap.put("page_url", page_url);
            paramMap.put("sign", sign);
            log.info("XinPayer_prepare_paramMap: {}", JSON.toJSONString(paramMap));
            result.setMessage(HtmlTemplateUtils.getPost(payment.getPayUrl() + "/forward/orderPay", paramMap));
            log.info("XinPayer_prepare_message: {}", result.getMessage());
        }
    }

    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        log.info("XinPayer_recharge_notify_resMap:{}", JSON.toJSONString(resMap));
        String mer_order_no = resMap.get("mer_order_no");// 商户订单号
        if (StringUtils.isEmpty(mer_order_no)) {
            return null;
        }
        return query(payment, mer_order_no);
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) {
        return notify(merchant, payment, resMap);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount payment, String orderId) {
        String keyValue = payment.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String mer_no = payment.getMerchantCode(); // 商户编号
        String mer_order_no = orderId; // 商户订单号
        String request_no = String.valueOf(System.currentTimeMillis());
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss");

        StringBuilder toSign = new StringBuilder();
        toSign.append("mer_no=").append(mer_no).append("&");
        toSign.append("mer_order_no=").append(mer_order_no).append("&");
        toSign.append("request_no=").append(request_no).append("&");
        toSign.append("request_time=").append(request_time).append("&");
        toSign.append("key=").append(keyValue);
        log.info("XinPayer_query_toSign: {}", toSign.toString());
        String sign = MD5.md5(toSign.toString()).toUpperCase();

        JSONObject paramMap = new JSONObject();
        paramMap.put("mer_no", mer_no);
        paramMap.put("mer_order_no", mer_order_no);
        paramMap.put("request_no", request_no);
        paramMap.put("request_time", request_time);
        paramMap.put("sign", sign);
        log.info("XinPayer_query_paramMap: {}", paramMap.toJSONString());
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/orderQuery", paramMap.toString(), requestHeader);

        log.info("XinPayer_query_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }

        String query_status = json.getString("query_status");
        String order_status = json.getString("order_status");

        if ("SUCCESS".equalsIgnoreCase(query_status) && "SUCCESS".equalsIgnoreCase(order_status)) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("order_no"));
            return pay;
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String mer_no = merchantAccount.getMerchantCode(); // 商户编号
        String mer_order_no = req.getOrderId(); // 商户订单号
        String acc_type = "1";
        String acc_no = req.getCardNo().trim();
        String acc_name = req.getName().trim();
        String ccy_no = "CNY";
        String order_amount = req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString();
        String province = "上海市";
        String city = "上海市";
        String asy_url = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("mer_no", mer_no);
        paramMap.put("mer_order_no", mer_order_no);
        paramMap.put("acc_type", acc_type);
        paramMap.put("acc_no", acc_no);
        paramMap.put("acc_name", acc_name);
        paramMap.put("ccy_no", ccy_no);
        paramMap.put("order_amount", order_amount);
        paramMap.put("province", province);
        paramMap.put("city", city);
        paramMap.put("asy_url", asy_url);

        String sign = MD5.md5(MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()).toUpperCase();
        log.info("XinPayer_doWithdraw_toSign:{}", sign);
        paramMap.put("sign", sign);
        log.info("XinPayer_doWithdraw_paramMap: {}", JSON.toJSONString(paramMap));
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/withdraw/singleOrder", JSON.toJSONString(paramMap), requestHeader);
        log.info("XinPayer_withdraw_resStr: {}", resStr);

        result.setResData(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || json.getString("status") == null || !"SUCCESS".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("err_msg"));
            return result;
        }

        String status = json.getString("status");

        result.setValid("SUCCESS".equals(status));
        result.setMessage(json.getString("err_msg"));
        result.setThirdOrderId(json.getString("order_no"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("XinPayer_doTransfer_notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("mer_order_no");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        // 商家设置用户购买商品的支付信息
        String request_no = String.valueOf(System.currentTimeMillis());
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss");
        String mer_no = merchant.getMerchantCode(); // 商户编号
        String mer_order_no = orderId; // 商户订单号

        StringBuilder toSign = new StringBuilder();
        toSign.append("mer_no=").append(mer_no).append("&");
        toSign.append("mer_order_no=").append(mer_order_no).append("&");
        toSign.append("request_no=").append(request_no).append("&");
        toSign.append("request_time=").append(request_time).append("&");
        toSign.append("key=").append(merchant.getPrivateKey());
        log.info("XinPayer_TransferQuery_toSign: {}", toSign.toString());
        String sign = MD5.md5(toSign.toString()).toUpperCase();

        JSONObject paramMap = new JSONObject();
        paramMap.put("mer_no", mer_no);
        paramMap.put("mer_order_no", mer_order_no);
        paramMap.put("request_no", request_no);
        paramMap.put("request_time", request_time);
        paramMap.put("sign", sign);
        log.info("XinPayer_TransferQuery_param: {}", paramMap.toJSONString());
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/withdraw/singleQuery", paramMap.toJSONString(), requestHeader);

        log.info("XinPayer_TransferQuery_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }

        String query_status = json.getString("query_status");
        String status = json.getString("status");

        if ("SUCCESS".equalsIgnoreCase(query_status)) {
            WithdrawNotify notify = new WithdrawNotify();
            notify.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            notify.setRemark(json.getString("query_err_msg"));
            notify.setThirdOrderId(json.getString("order_no"));
            if (status.equals("SUCCESS")) {
                notify.setStatus(0);
            } else if (status.equals("FAIL")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
            notify.setSuccessTime(new Date());
            return notify;

        }
        return null;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String keyValue = merchantAccount.getPrivateKey(); // 商家密钥
        // 商家设置用户购买商品的支付信息
        String mer_no = merchantAccount.getMerchantCode(); // 商户编号
        String request_no = String.valueOf(System.currentTimeMillis());
        String request_time = DateUtils.format(new Date(), "yyyyMMddHHmmss");

        StringBuilder toSign = new StringBuilder();
        toSign.append("mer_no=").append(mer_no).append("&");
        toSign.append("request_no=").append(request_no).append("&");
        toSign.append("request_time=").append(request_time).append("&");
        toSign.append("key=").append(keyValue);
        log.info("XinPayer_balance_query_toSign: {}", toSign.toString());
        String sign = MD5.md5(toSign.toString()).toUpperCase();

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("mer_no", mer_no);
        paramMap.put("request_no", request_no);
        paramMap.put("request_time", request_time);
        paramMap.put("sign", sign);
        log.info("XinPayer_balance_req_param: {}", JSONObject.toJSONString(paramMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.XIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.XIN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/withdraw/balanceQuery", JSON.toJSONString(paramMap), requestHeader);

        log.info("XinPayer_balance_resStr: {}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }

        BigDecimal balance = json.getBigDecimal("balance");
        return balance == null ? BigDecimal.ZERO : balance.divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
    }

}
