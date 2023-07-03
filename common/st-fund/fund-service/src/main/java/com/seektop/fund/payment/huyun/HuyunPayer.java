package com.seektop.fund.payment.huyun;

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
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 虎云支付
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HUYUNPAY + "")
public class HuyunPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private GlRechargeMapper glRechargeMapper;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        String keyValue = account.getPrivateKey();// 私钥
        Map<String, String> param = new LinkedHashMap<>();
        param.put("version", "1.0");// 版本号
        param.put("customerid", account.getMerchantCode());// 商户编号
        param.put("userid", req.getUserId().toString());
        param.put("total_fee", req.getAmount().setScale(2, RoundingMode.DOWN).toString());// 订单金额
        param.put("sdorderno", req.getOrderId());// 商户订单号
        param.put("notifyurl", account.getNotifyUrl() + merchant.getId());// 异步通知URL
        param.put("returnurl", account.getResultUrl() + merchant.getId());// 同步跳转URL

        String toSign = MD5.toSign(param) + "&" + keyValue;

        param.put("bankcode", paymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));// 银行编号
        param.put("sign", MD5.md5(toSign));

        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER
                || merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            param.put("paytype", "bank");// 支付编号-银行卡转账
            if (account.getMerchantCode().startsWith("110")) {
                param.put("paytype", "alipaywap");
                log.info("HuyunPayer_prepare_param: {}", param);
            }
            result.setMessage(HtmlTemplateUtils.getPost(account.getPayUrl() + "/apisubmit", param));
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            param.put("paytype", "ysf");// 支付编号-云闪付
            log.info("HuyunPayer_prepare_param: {}", param);
            result.setMessage(HtmlTemplateUtils.getPost("https://union.cc8859.com/apisubmit", param));
        }

        log.info("HuyunPayer_prepare_resStr:{}", result.getMessage());
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) {
        return notify(merchant, account, resMap);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) {
        log.info("HuyunPayer_notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("sdorderno");// 商户订单号
        if (StringUtils.isEmpty(orderId)) {
            return null;
        }
        return query(account, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) {
        String keyValue = account.getPrivateKey();// 私钥
        Map<String, String> param = new LinkedHashMap<>();
        param.put("customerid", account.getMerchantCode());//商户编号
        param.put("sdorderno", orderId);//商户订单号
        param.put("reqtime", DateUtils.getCurrDateStr14());//时间戳

        String toSign = MD5.toSign(param) + "&" + keyValue;
        log.info("HuyunPayer_query_toSign:{}", toSign);

        param.put("sign", MD5.md5(toSign));

        log.info("HuyunPayer_query_param:{}", param);

        GlRecharge glRecharge = glRechargeMapper.selectByPrimaryKey(orderId);
        String resStr = "";
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HUYUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUYUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        if (glRecharge.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER
                || glRecharge.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            resStr = okHttpUtil.post(account.getPayUrl() + "/apiorderquery", param, requestHeader);
        } else if (glRecharge.getPaymentId() == FundConstant.PaymentType.UNION_PAY) {
            resStr = okHttpUtil.post("https://union.cc8859.com/apiorderquery", param, requestHeader);
        }

        log.info("HuyunPayer_query_resStr:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (ObjectUtils.isEmpty(json)) {
            return null;
        }

        if ("0".equals(json.getString("status"))) {// 失败
            return null;
        }

        RechargeNotify pay = new RechargeNotify();

        pay.setAmount(glRecharge == null ? BigDecimal.ZERO : glRecharge.getAmount());
        pay.setFee(BigDecimal.ZERO);
        pay.setOrderId(orderId);
        pay.setThirdOrderId(json.getString("sdpayno"));// 平台订单号
        return pay;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) throws GlobalException {

        String keyValue = account.getPrivateKey();// 私钥

        Map<String, String> param = new LinkedHashMap<>();

        param.put("customerid", account.getMerchantCode());// 商户编号
        param.put("sdorderno", req.getOrderId());// 商户订单号
        param.put("total_fee", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());// 订单金额
        param.put("accountname", req.getName());// 账户名
        param.put("bankcode", paymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));// 银行编码
        param.put("cardno", req.getCardNo());// 银行卡号
        param.put("province", "上海");// 开户省份
        param.put("city", "上海");// 开户城市
        param.put("branchname", req.getBankName());// 开户支行
        param.put("notifyurl", account.getNotifyUrl() + account.getMerchantId());// 异步通知URL

        String toSign = MD5.toSign(param) + "&" + keyValue;
        log.info("HuyunPayer_doTransfer_toSign: {}", toSign);
        param.put("sign", MD5.md5(toSign));// md5签名串
        log.info("HuyunPayer_doTransfer_param: {}", param);

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.HUYUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUYUN_PAY.getPaymentName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/apicash", param, requestHeader);

        log.info("HuyunPayer_doTransfer_resStr: {}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(param));
        result.setResData(resStr);

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"200".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : SecureUtil.unicodeToUtf8(json.getString("msg")));
            return result;
        }

        result.setValid(true);
        result.setMessage(json.getString("msg"));

        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount account, Map<String, String> resMap) {
        log.info("HuyunPayer_doTransferNotify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("sdorderno");
        return doTransferQuery(account, orderId);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount account, String orderId) {

        String keyValue = account.getPrivateKey();// 私钥

        Map<String, String> param = new LinkedHashMap<>();

        param.put("customerid", account.getMerchantCode());// 商户编号
        param.put("sdorderno", orderId);// 订单ID

        String toSign = MD5.toSign(param) + "&" + keyValue;
        log.info("HuyunPayer_doTransferQuery_toSign:{}", toSign);
        param.put("sign", MD5.md5(toSign));// md5验证签名串
        log.info("HuyunPayer_doTransferQuery_toSign:{}", param);

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.HUYUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUYUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/apicashquery", param, requestHeader);

        log.info("HuyunPayer_doTransferQuery_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return null;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (ObjectUtils.isEmpty(json)) {
            return null;
        }

        WithdrawNotify notify = new WithdrawNotify();
        notify.setOrderId(orderId);
        notify.setMerchantCode(account.getMerchantCode());
        notify.setMerchantId(account.getMerchantId());
        notify.setMerchantName(account.getChannelName());
        notify.setRemark("");
        if (!"200".equals(json.getString("status"))) {// 失败
            notify.setStatus(1);
            notify.setAmount(BigDecimal.ZERO);
            return notify;
        }

        JSONObject data = json.getJSONObject("data");
        if (null == data || !"1".equals(data.getString("is_state"))) {// 失败
            notify.setStatus(1);
            notify.setAmount(BigDecimal.ZERO);
            return notify;
        }

        notify.setAmount(data.getBigDecimal("money").subtract(data.getBigDecimal("fee")));
        notify.setThirdOrderId(data.getString("orderid"));
        notify.setStatus(0);
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount account) {

        Map<String, String> param = new HashMap<>();
        param.put("customerid", account.getMerchantCode());
        String sign = MD5.md5("customerid=" + account.getMerchantCode() + "&" + account.getPrivateKey());
        param.put("sign", sign);

        log.info("HuyunPayer_queryBalance_resStr:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.HUYUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUYUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/apibalance", param, requestHeader);
        log.info("HuyunPayer_queryBalance_resStr:{}", resStr);

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO;
        }

        JSONObject json = JSON.parseObject(resStr);

        if (ObjectUtils.isEmpty(json)) {
            return BigDecimal.ZERO;
        }

        if (!"0".equals(json.getString("status"))) {
            return BigDecimal.ZERO;
        }

        return json.getBigDecimal("balance");
    }
}
