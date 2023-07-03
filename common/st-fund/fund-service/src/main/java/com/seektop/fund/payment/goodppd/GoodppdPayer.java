package com.seektop.fund.payment.goodppd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 好付支付接口
 *
 * @author rick
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HAOFU_PAY_117 + "")
public class GoodppdPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "100501");
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result, "100601");
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            prepareToWap(merchant, account, req, result);
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            prepareToWangyin(merchant, account, req, result);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String busiCode) {
        Map<String, String> param = new HashMap<>();
        param.put("bg_url", account.getNotifyUrl() + merchant.getId());
        param.put("busi_code", busiCode);
        param.put("goods", "CZ");
        param.put("mer_no", account.getMerchantCode());
        param.put("mer_order_no", req.getOrderId());
        param.put("order_amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString());
        // sign 签名数据
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();

        log.info("GoodppdPayer_PrepareToScan_toSign:{}", toSign);
        String sign = MD5.md5(toSign);
        param.put("sign", sign);

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_117.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_117.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/gpment/scanSub", JSON.toJSONString(param), requestHeader);
        log.info("GoodppdPayer_PrepareToScan_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (null == json || !json.getString("status").equals("SUCCESS")) {
            throw new RuntimeException("创建订单失败");
        }
        String code_url = json.getString("code_url");
        code_url = this.getFromBase64(code_url);
        log.info("GoodppdPayer_PrepareToScan_codeURL:{}", code_url);

        result.setMessage(HtmlTemplateUtils.getQRCode(code_url));
    }

    public String getFromBase64(String s) {
        byte[] b = null;
        String result = null;
        if (s != null) {
            Base64.Decoder decoder = Base64.getDecoder();
            try {
                b = decoder.decode(s);
                result = new String(b, "utf-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private void prepareToWap(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> param = new HashMap<>();
        param.put("bg_url", account.getNotifyUrl() + merchant.getId());
        param.put("busi_code", "100401");
        param.put("goods", "CZ");
        param.put("mer_no", account.getMerchantCode());
        param.put("mer_order_no", req.getOrderId());
        param.put("order_amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString());
        // sign 签名数据
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();

        log.info("GoodppdPayer_PrepareToWap_toSign:{}", toSign);
        String sign = MD5.md5(toSign);
        param.put("sign", sign);
        String formStr = HtmlTemplateUtils.getPost(account.getPayUrl() + "/form/jumpSub", param);
        result.setMessage(formStr);
        log.info("GoodppdPayer_PrepareToWap_formStr:{}", formStr);
    }

    private void prepareToWangyin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        Map<String, String> param = new HashMap<>();
        param.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
        param.put("bg_url", account.getNotifyUrl() + merchant.getId());
        param.put("busi_code", "100301");
        param.put("goods", "CZ");
        param.put("mer_no", account.getMerchantCode());
        param.put("mer_order_no", req.getOrderId());
        param.put("order_amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString());
        param.put("page_url", account.getResultUrl() + merchant.getId());
        // sign 签名数据
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();

        log.info("GoodppdPayer_PrepareToWangyin_toSign:{}", toSign);
        String sign = MD5.md5(toSign);
        param.put("sign", sign);
        String formStr = HtmlTemplateUtils.getPost(account.getPayUrl() + "/form/jumpSub", param);
        result.setMessage(formStr);
        log.info("GoodppdPayer_PrepareToWangyin_formStr:{}", formStr);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) {
        log.info("GoodppdPayer_notify_resMap:{}", JSON.toJSONString(resMap));
        String mer_order_no = resMap.get("mer_order_no");
        if (StringUtils.isEmpty(mer_order_no)) {
            return null;
        }
        return query(account, mer_order_no);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) {

        Map<String, String> param = new HashMap<>();
        param.put("mer_no", account.getMerchantCode());
        param.put("mer_order_no", orderId);
        param.put("request_no", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS2));
        param.put("request_time", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));

        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();

        log.info("GoodppdPayer_query_toSign:{}", toSign);
        param.put("sign", MD5.md5(toSign));

        log.info("GoodppdPayer_query_paramMap:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_117.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_117.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/gpment/singleQuery", JSON.toJSONString(param), requestHeader);
        log.info("GoodppdPayer_query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (null == json) {
            return null;
        }
        if (null == json.getString("query_status") || !json.getString("query_status").equals("SUCCESS")) {
            return null;
        }
        if (!json.getString("order_status").equals("SUCCESS")) {
            return null;
        }
        RechargeNotify pay = new RechargeNotify();
        pay.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)));
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("order_no"));
        return pay;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) {
        return notify(merchant, account, resMap);
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) {
        String key = account.getPrivateKey();

        Map<String, String> param = new HashMap<>();
        param.put("acc_name", req.getName());// 收款户名
        param.put("acc_no", req.getCardNo());// 收款账号
        param.put("acc_type", "1");// 账户类型  1:对私 2：对公
        param.put("city", "上海市");// 城市
        param.put("mer_no", account.getMerchantCode());// 省份
        param.put("mer_order_no", req.getOrderId());// 商户订单号
        param.put("order_amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.DOWN).toString());//金额
        param.put("province", "上海市");// 省份

        String toSign = MD5.toAscii(param) + "&key=" + key;
        log.info("GoodppdPayer_doTransfer_toSign:{}", toSign);

        String sign = MD5.md5(toSign);
        param.put("sign", sign);
        log.info("GoodppdPayer_doTransfer_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_117.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_117.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/trans/paidSub", JSON.toJSONString(param), requestHeader);
        log.info("GoodppdPayer_doTransfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(param));
        result.setResData(resStr);

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("第三方接口错误");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (json == null || !"SUCCESS".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json == null ? "第三方接口错误" : json.getString("err_msg"));
            return result;
        }

        String TransStatus = json.getString("status");

        result.setValid("SUCCESS".equals(TransStatus));
        result.setMessage(json.getString("err_msg"));
        result.setThirdOrderId(json.getString("order_no"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount account, Map<String, String> resMap) {

        log.info("GoodppdPayer_doTransferNotify_resMap:{}", JSON.toJSONString(resMap));
        String mer_order_no = resMap.get("mer_order_no");
        if (StringUtils.isEmpty(mer_order_no)) {
            return null;
        }
        return doTransferQuery(account, mer_order_no);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount account, String order_no) {
        Map<String, String> param = new HashMap<>();
        param.put("mer_no", account.getMerchantCode());
        param.put("mer_order_no", order_no);
        String request_time = DateUtils.getCurrDateStr14();
        param.put("request_no", request_time);
        param.put("request_time", request_time);

        StringBuffer signbuf = new StringBuffer();
        signbuf.append("mer_no=" + param.get("mer_no"));
        signbuf.append("&mer_order_no=" + param.get("mer_order_no"));
        signbuf.append("&request_no=" + param.get("request_no"));
        signbuf.append("&request_time=" + param.get("request_time"));
        signbuf.append("&key=" + account.getPrivateKey());

        log.info("GoodppdPayer_doTransferQuery_sign:{}", signbuf.toString());
        param.put("sign", MD5.md5(signbuf.toString()));

        log.info("GoodppdPayer_doTransferQuery_paramMap:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_117.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_117.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(order_no)
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/trans/paidQuery", JSON.toJSONString(param), requestHeader);
        log.info("GoodppdPayer_doTransferQuery_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (ObjectUtils.isEmpty(json) || !"SUCCESS".equals(json.getString("query_status"))
                || !"SUCCESS".equals(json.getString("status"))) {
            return null;
        }

        WithdrawNotify result = new WithdrawNotify();
        result.setAmount(json.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)));
        result.setMerchantCode(account.getMerchantCode());
        result.setMerchantId(account.getMerchantId());
        result.setMerchantName(account.getChannelName());
        result.setOrderId(order_no);
        result.setRemark(json.getString("err_msg"));
        result.setThirdOrderId(json.getString("order_no"));
        result.setStatus(0);
        result.setSuccessTime(json.getDate("request_time"));

        return result;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount account) {

        Map<String, String> param = new HashMap<>();
        param.put("mer_no", account.getMerchantCode());
        String request_time = DateUtils.getCurrDateStr14();
        param.put("request_no", request_time);
        param.put("request_time", request_time);
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();
        log.info("GoodppdPayer_queryBalance_toSign:{}", toSign);
        param.put("sign", MD5.md5(toSign));
        log.info("GoodppdPayer_queryBalance_param:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.HAOFU_PAY_117.getCode() + "")
                .channelName(PaymentMerchantEnum.HAOFU_PAY_117.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/trans/accountQuery", JSON.toJSONString(param), requestHeader);
        log.info("GoodppdPayer_queryBalance_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (null == json || !json.getBoolean("status")) {
            return BigDecimal.ZERO;
        }
        return json.getBigDecimal("balance").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN);
    }
}
