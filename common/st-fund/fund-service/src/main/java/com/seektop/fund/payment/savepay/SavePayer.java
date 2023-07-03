package com.seektop.fund.payment.savepay;

import com.alibaba.fastjson.JSON;
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
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 安心付
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.SAVEPAY + "")
public class SavePayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    private static Map<Integer, String> bankMap = new HashMap<>();

    static {
        bankMap.put(1, "CMB"); //招商银行
        bankMap.put(2, "ICBC"); //工商银行
        bankMap.put(3, "CCB"); //建设银行
        bankMap.put(4, "ABC"); //农业银行
        bankMap.put(5, "BOC"); //中国银行
        bankMap.put(6, "BOCOM"); //交通银行
        bankMap.put(7, "GDB"); //广发银行
        bankMap.put(8, "CEB"); //光大银行
        bankMap.put(9, "SPDB"); //浦发银行
        bankMap.put(10, "CMBC"); //民生银行
        bankMap.put(11, "PINAN"); //平安银行
        bankMap.put(12, "CIB"); //兴业银行
        bankMap.put(13, "CITIC"); //中信银行
        bankMap.put(14, "PSBC"); //邮政银行
        bankMap.put(15, "HXB"); //华夏银行
    }

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

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

        String parter = payment.getMerchantCode(); // 用户编号
        String tyid = "102";
        String type = paymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()); // 银行类型
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            tyid = "98";
            type = "992";
            if (req.getClientType() != ProjectConstant.ClientType.PC) {
                tyid = "980";
                type = "1006";
            }
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            tyid = "101";
            type = "1021";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            tyid = "99";
            type = "991";
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            tyid = "202";
            type = "1010";
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            tyid = "1020";
            type = "1005";
        }
        String value = req.getAmount().setScale(2, RoundingMode.DOWN).toString();
        String orderid = req.getOrderId();
        String callbackurl = payment.getNotifyUrl() + merchant.getId();
        String hrefbackurl = payment.getResultUrl() + merchant.getId();

        String sign = MD5.md5("parter=" + parter + "&type=" + type + "&value=" + value + "&orderid=" + orderid + "&tyid=" + tyid + "&callbackurl=" + callbackurl + keyValue);
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("parter", parter);
        paramMap.put("type", type);
        paramMap.put("value", value);
        paramMap.put("orderid", orderid);
        paramMap.put("tyid", tyid);
        paramMap.put("callbackurl", callbackurl);
        paramMap.put("hrefbackurl", hrefbackurl);
        paramMap.put("sign", sign);

        log.info("SavePayer_prepare_paramMap: {}", JSON.toJSONString(paramMap));
        String depositURL = payment.getPayUrl() + "/Bank/";
        result.setMessage(HtmlTemplateUtils.getPost(depositURL, paramMap));
        log.info("SavePayer_prepare_html: {}", result.getMessage());
    }


    /**
     * 支付返回结果校验
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        log.info("SavePayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderid");
        String opstate = resMap.get("opstate");
        String ovalue = resMap.get("ovalue");
        String reSign = resMap.get("sign");
        if (StringUtils.isEmpty(orderid) || StringUtils.isEmpty(opstate) || StringUtils.isEmpty(ovalue) || StringUtils.isEmpty(reSign)) {
            return null;
        }
        String key = payment.getPrivateKey();

        String signStr = "orderid=" + orderid + "&opstate=" + opstate + "&ovalue=" + ovalue + key;
        String sign = MD5.md5(signStr);
        if (sign.equals(reSign) && opstate.equals("0")) {
            RechargeNotify pay = this.query(payment, orderid);
            if (null != pay) {
                pay.setThirdOrderId(resMap.get("sysorderid"));
                return pay;
            }
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String keyValue = account.getPrivateKey(); // 商家密钥
        String parter = account.getMerchantCode(); // 用户编号
        String orderid = orderId;
        String sign = MD5.md5("orderid=" + orderid + "&parter=" + parter + keyValue);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("parter", parter);
        paramMap.put("orderid", orderid);
        paramMap.put("sign", sign);

        log.info("SavePayer_Query_paramMap: {}", JSON.toJSONString(paramMap));
        String queryURL = account.getPayUrl() + "/search.aspx";
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.SAVEPAY.getCode() + "")
                .channelName(PaymentMerchantEnum.SAVEPAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderid)
                .build();
        String resStr = okHttpUtil.get(queryURL, paramMap, requestHeader);
        log.info("SavePayer_Query_resStr: {}", resStr);

        String res[] = resStr.split("&");
        Map<String, String> resultMap = new HashMap<>();
        for (String re : res) {
            String[] r = re.split("=");
            if (r.length == 2) {
                resultMap.put(r[0].trim(), r[1].trim());
            }
        }

        if ("0".equals(resultMap.get("opstate"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(BigDecimal.valueOf(Double.valueOf(resultMap.get("ovalue"))).setScale(4, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
            return pay;
        }
        return null;
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, payment, resMap);
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {

        try {
            String keyValue = merchantAccount.getPrivateKey();
            PublicKey publicKey = RSAUtil.getPublicKey(merchantAccount.getPublicKey());
            PrivateKey privateKey = RSAUtil.getPrivateKey(keyValue);
            // 商家设置用户购买商品的支付信息
            String version = "1.0";
            String trancode = "10001";
            String parter = merchantAccount.getMerchantCode();
            String orderid = req.getOrderId();
            String trantime = DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS);
            // 三方固定每笔收款5元手续费、请求金额 100，银行卡到账金额95
            String amount = req.getAmount().subtract(req.getFee()).add(BigDecimal.valueOf(5)).setScale(2, RoundingMode.DOWN).toString();
            String callbackurl = merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId();
            String trantype = "0";
            String accattr = "0";
            String bankcode = bankMap.get(req.getBankId());
            String accname = RSAUtil.encryptByPublicKey(req.getName().getBytes(Charset.forName("utf-8")), publicKey);
            String accno = RSAUtil.encryptByPublicKey(req.getCardNo().getBytes(Charset.forName("utf-8")), publicKey);
            String province = new String("上海市".getBytes("UTF-8"));
            String city = new String("上海市".getBytes("UTF-8"));
            String branchname = new String(req.getBankName().getBytes("UTF-8"));
            String signtype = "RSA";
            String channelid = "";
            String phone = "13800138000";
            TreeMap<String, String> paramMap = new TreeMap<>();
            paramMap.put("version", version);
            paramMap.put("trancode", trancode);
            paramMap.put("parter", parter);
            paramMap.put("orderid", orderid);
            paramMap.put("trantime", trantime);
            paramMap.put("channelid", channelid);
            paramMap.put("amount", amount);
            paramMap.put("callbackurl", callbackurl);
            paramMap.put("trantype", trantype);
            paramMap.put("accattr", accattr);
            paramMap.put("bankcode", bankcode);
            paramMap.put("accname", accname);
            paramMap.put("accno", accno);
            paramMap.put("phone", phone);
            paramMap.put("province", province);
            paramMap.put("city", city);
            paramMap.put("branchname", branchname);
            paramMap.put("branchno", "");
            paramMap.put("attach", "");
            paramMap.put("signtype", signtype);
            String sign = RSAUtil.sign(paramMap, privateKey);
            paramMap.put("sign", sign);
            log.info("SavePayer_transfer_paramMap: {}", JSON.toJSONString(paramMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW.getCode())
                    .channelId(PaymentMerchantEnum.SAVEPAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.SAVEPAY.getPaymentName())
                    .userId(req.getUserId().toString())
                    .userName(req.getUsername())
                    .tradeId(orderid)
                    .build();
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl(), paramMap, requestHeader);
            log.info("SavePayer_transfer_resStr: {}", resStr);

            WithdrawResult result = new WithdrawResult();
            result.setOrderId(req.getOrderId());
            result.setReqData(JSON.toJSONString(paramMap));
            result.setResData(resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (StringUtils.isEmpty(resStr) || null == json) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }

            String code = json.getString("code");
            JSONObject data = json.getJSONObject("data");
            if (data == null || !code.equals("0")) {
                result.setValid(false);
                result.setMessage(json.getString("msg"));
                return result;
            }
            result.setValid(true);
            result.setMessage(json.getString("msg"));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("SavePayer_doTransfer_notify:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String keyValue = merchant.getPrivateKey();

        try {
            PrivateKey privateKey = RSAUtil.getPrivateKey(keyValue);

            TreeMap<String, String> paramMap = new TreeMap<>();
            paramMap.put("version", "1.0");
            paramMap.put("trancode", "20001");
            paramMap.put("parter", merchant.getMerchantCode());
            paramMap.put("orderid", orderId);
            paramMap.put("trantime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));
            paramMap.put("signtype", "RSA");

            String sign = RSAUtil.sign(paramMap, privateKey);
            paramMap.put("sign", sign);

            log.info("SavePayer_transfer_query_paramMap: {}", JSON.toJSONString(paramMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.SAVEPAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.SAVEPAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId)
                    .build();
            String resStr = okHttpUtil.post(merchant.getPayUrl(), paramMap, requestHeader);
            log.info("SavePayer_transfer_query_resStr: {}", resStr);

            if (StringUtils.isEmpty(resStr)) {
                return null;
            }
            JSONObject json = JSON.parseObject(resStr);
            if (json == null) {
                return null;
            }
            Integer code = json.getInteger("code");
            JSONObject data = json.getJSONObject("data");
            if (data == null || code != 0) {
                return null;
            }

            WithdrawNotify notify = new WithdrawNotify();
            notify.setAmount(data.getBigDecimal("amount"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            notify.setThirdOrderId(data.getString("tranorderid"));
            if (data.getIntValue("code") == 1) {
                notify.setStatus(0);
            } else if (data.getIntValue("code") == -1) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }

            if (notify.getStatus() != 0) {
                notify.setAmount(null);
            }
            return notify;
        } catch (Exception e) {
            e.printStackTrace();
        }


        return null;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        try {
            String keyValue = merchantAccount.getPrivateKey();

            PrivateKey privateKey = RSAUtil.getPrivateKey(keyValue);
            TreeMap<String, String> paramMap = new TreeMap<>();
            paramMap.put("version", "1.0");
            paramMap.put("trancode", "30001");
            paramMap.put("parter", merchantAccount.getMerchantCode());
            paramMap.put("signtype", "RSA");

            String sign = RSAUtil.sign(paramMap, privateKey);
            paramMap.put("sign", sign);

            log.info("SavePayer_query_balance_paramMap: {}", JSON.toJSONString(paramMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                    .channelId(PaymentMerchantEnum.SAVEPAY.getCode() + "")
                    .channelName(PaymentMerchantEnum.SAVEPAY.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId("")
                    .build();
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl(), paramMap, requestHeader);
            log.info("SavePayer_query_balance_resStr: {}", resStr);
            if (StringUtils.isNotEmpty(resStr)) {
                JSONObject json = JSON.parseObject(resStr);
                if (json != null && json.getString("code").equals("0")) {
                    JSONObject data = json.getJSONObject("data");
                    return data.getBigDecimal("balance");
                }
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.info("SavePayer_query_balance_error:{}", e);
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

}
