package com.seektop.fund.payment.goodpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
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
import java.util.*;

@Slf4j
@Service(FundConstant.PaymentChannel.GOODPAY + "")
public class GoodPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            prepareWangyin(merchant, account, req, result);
        }
    }

    /**
     * WEB端网银
     */
    private void prepareWangyin(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) {
        try {
            String keyValue = account.getPrivateKey();  //商户接口秘钥
            String version = "v1";//接口版本
            String merchant_no = account.getMerchantCode();//商户号
            String order_no = req.getOrderId();//商户订单号
            String goods_name = "CZ";//商品名称
            String order_amount = req.getAmount().setScale(2, RoundingMode.DOWN).toString();//订单金额
            String backend_url = account.getNotifyUrl() + merchant.getId();//支付结果异步通知地址
            String frontend_url = account.getResultUrl() + merchant.getId();//支付结果同步通知地址
            String reserve = "";//商户保留信息
            String pay_mode = "01";//支付模式
            String bank_code = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId());//银行编号
            String card_type = "0";//允许支付的银行卡类型

            goods_name = Base64.getEncoder().encodeToString(goods_name.getBytes("utf-8"));

            //MD5签名
            StringBuilder toSign = new StringBuilder();
            toSign.append("version=").append(version).append("&");
            toSign.append("merchant_no=").append(merchant_no).append("&");
            toSign.append("order_no=").append(order_no).append("&");
            toSign.append("goods_name=").append(goods_name).append("&");
            toSign.append("order_amount=").append(order_amount).append("&");
            toSign.append("backend_url=").append(backend_url).append("&");
            toSign.append("frontend_url=").append(frontend_url).append("&");
            toSign.append("reserve=").append(reserve).append("&");
            toSign.append("pay_mode=").append(pay_mode).append("&");
            toSign.append("bank_code=").append(bank_code).append("&");
            toSign.append("card_type=").append(card_type).append("&");
            toSign.append("key=").append(keyValue);
            log.info("GoodPayer_Prepare_toSign:{}", toSign);
            String sign = MD5.md5(toSign.toString());

            Map<String, String> param = new LinkedHashMap<>();
            param.put("version", version);
            param.put("merchant_no", merchant_no);
            param.put("order_no", order_no);
            param.put("goods_name", goods_name);
            param.put("order_amount", order_amount);
            param.put("backend_url", backend_url);
            param.put("frontend_url", frontend_url);
            param.put("reserve", "");
            param.put("pay_mode", "01");
            param.put("bank_code", bank_code);
            param.put("card_type", "0");
            param.put("sign", sign);

            log.info("GoodPayer_recharge_prepareWangyin_params:{}", JSON.toJSONString(param));
            String retBack = HtmlTemplateUtils.getPost(account.getPayUrl() + "/gateway/pay.jsp", param);
            if (StringUtils.isEmpty(retBack)) {
                throw new RuntimeException("创建订单失败");
            }
            log.info("GoodPayer_recharge_prepareWangyin_result:{}", retBack);
            result.setMessage(retBack);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("创建订单失败");
        }
    }


    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("GoodPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String merchant_no = resMap.get("merchant_no");
        String orderId = resMap.get("order_no");
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(merchant_no) && merchant_no.equals(account.getMerchantCode())) {
            return query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {

        Map<String, String> param = new HashMap<>();
        param.put("merchant_no", account.getMerchantCode());
        param.put("order_no", orderId);
        String signStr = "merchant_no=" + account.getMerchantCode() + "&order_no=" + orderId + "&key=" + account.getPrivateKey();
        param.put("sign", MD5.md5(signStr).toLowerCase());

        log.info("GoodPayer_query_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.GOOD_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.GOOD_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/gateway/queryOrder.jsp", param, requestHeader);

        log.info("GoodPayer_query_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);

        if (json == null) {
            return null;
        }

        String result_code = json.getString("result_code");
        String status = json.getString("result");
        if (StringUtils.isNotEmpty(result_code) && StringUtils.isNotEmpty(status) && "000000".equals(result_code) && status.equals("S")) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("order_amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("trace_id"));
            return pay;
        }

        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) throws GlobalException {

        String pay_pwd = account.getPublicKey(); //支付密钥
        String key = account.getPrivateKey(); //接口密钥
        Map<String, String> param = new LinkedHashMap<>();

        String merchant_no = account.getMerchantCode();//商户号
        String order_no = req.getOrderId();//商户订单号
        String card_no = req.getCardNo();//银行卡号
        String account_name = req.getName();//银行开户名
        String bank_branch = "";//银行支行名称
        String cnaps_no = "";//银行联行号
        String amount = req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString();//代付金额
        String bank_code = glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId());//银行代码
        String bank_name = glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId());//银行名称
        try {
            account_name = Base64.getEncoder().encodeToString(account_name.getBytes("utf-8"));//Base64编码
            bank_name = Base64.getEncoder().encodeToString(bank_name.getBytes("utf-8"));
            if (bank_branch != null && bank_branch.trim().length() > 0) {
                bank_branch = Base64.getEncoder().encodeToString(bank_branch.getBytes("utf-8"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("创建提现订单失败");
        }

        StringBuilder toSign = new StringBuilder();
        toSign.append("merchant_no=").append(merchant_no).append("&");
        toSign.append("order_no=").append(order_no).append("&");
        toSign.append("card_no=").append(card_no).append("&");
        toSign.append("account_name=").append(account_name).append("&");
        toSign.append("bank_branch=").append(bank_branch).append("&");
        toSign.append("cnaps_no=").append(cnaps_no).append("&");
        toSign.append("bank_code=").append(bank_code).append("&");
        toSign.append("bank_name=").append(bank_name).append("&");
        toSign.append("amount=").append(amount).append("&");
        toSign.append("pay_pwd=").append(pay_pwd).append("&");
        toSign.append("key=").append(key);
        String sign = MD5.md5(toSign.toString());


        param.put("merchant_no", merchant_no);
        param.put("order_no", order_no);
        param.put("card_no", card_no);
        param.put("account_name", account_name);
        param.put("bank_branch", bank_branch);
        param.put("cnaps_no", "");
        param.put("bank_code", bank_code);
        param.put("bank_name", bank_name);
        param.put("amount", amount);
        param.put("sign", sign);
        log.info("GoodPayer_doTransfer_param: {}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.GOOD_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.GOOD_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/withdraw/singleWithdraw", param, requestHeader);
        log.info("GoodPayer_doTransfer_resStr: {}", resStr);
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(param));
        JSONObject json = JSON.parseObject(resStr);
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        if (json == null) {
            return result;
        }
        result.setMessage(json.getString("result_msg"));
        String result_code = json.getString("result_code");

        if (StringUtils.isEmpty(result_code) || !"000000".equals(result_code)) {
            result.setValid(false);
            return result;
        }
        String status = json.getString("result");
        if (StringUtils.isEmpty(status) || status.equals("F")) {
            result.setValid(false);
            return result;
        }
        result.setValid(true);
        result.setThirdOrderId(json.getString("order_no"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("GoodPayer_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String message = resMap.get("reqBody");

        JSONObject json = JSON.parseObject(message);
        if (null == json) {
            return null;
        }
        String merchant_no = json.getString("merchant_no");
        JSONArray orderList = JSONArray.parseArray(json.getString("orders"));
        if (null == orderList || orderList.size() == 0) {
            return null;
        }
        String orderStr = orderList.get(0).toString();
        JSONObject orderData = JSON.parseObject(orderStr);
        if (null == orderData || StringUtils.isEmpty(orderData.getString("order_no"))) {
            return null;
        }
        String orderId = orderData.getString("mer_order_no");
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(merchant_no) && merchant_no.equals(merchant.getMerchantCode())) {
            return doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount account, String orderId) throws GlobalException {
        String privateKey = account.getPrivateKey();
        Map<String, String> param = new HashMap<>();
        param.put("merchant_no", account.getMerchantCode());
        param.put("order_no", orderId);

        StringBuilder toSign = new StringBuilder();
        toSign.append("merchant_no=").append(account.getMerchantCode()).append("&");
        toSign.append("order_no=").append(orderId).append("&");
        toSign.append("key=").append(privateKey);
        param.put("sign", MD5.md5(toSign.toString()));

        log.info("GoodPayer_TransferQuery_reqMap:{}", param);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.GOOD_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.GOOD_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/withdraw/queryOrder", param, requestHeader);
        log.info("GoodPayer_TransferQuery_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        String result = json.getString("result");
        String result_code = json.getString("result_code");

        WithdrawNotify notify = new WithdrawNotify();
        notify.setMerchantCode(account.getMerchantCode());
        notify.setMerchantId(account.getMerchantId());
        notify.setMerchantName(account.getChannelName());
        notify.setOrderId(orderId);

        if (StringUtils.isEmpty(result_code) || StringUtils.isEmpty(result) || !"000000".equals(result_code) || !result.equals("S")) {
            notify.setStatus(2);
            if (result.equals("F")) {
                notify.setStatus(1);
            }
            notify.setRemark(json.getString("result_msg"));
            return notify;
        }
        BigDecimal changeAmount = json.getBigDecimal("amount");
        BigDecimal withdrawFee = json.getBigDecimal("withdraw_fee");

        notify.setStatus(0);
        notify.setAmount(changeAmount.subtract(withdrawFee));
        notify.setThirdOrderId(json.getString("order_no"));
        notify.setSuccessTime(new Date());
        notify.setRemark(json.getString("result_msg"));
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount account) throws GlobalException {

        String privateKey = account.getPrivateKey();// 接口密钥
        Map<String, String> param = new HashMap<>();
        param.put("merchant_no", account.getMerchantCode());
        String sign = MD5.md5("merchant_no=" + account.getMerchantCode() + "&key=" + privateKey).toLowerCase();
        param.put("sign", sign);
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.GOOD_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.GOOD_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + "/withdraw/queryBalance", param, requestHeader);
        log.info("GoodPayer_QueryBalance_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (null == json) {
            return BigDecimal.ZERO;
        }

        String resultCode = json.getString("result_code");
        if (StringUtils.isNotEmpty(resultCode) && resultCode.equals("000000")) {
            return json.getBigDecimal("balance");
        }
        return BigDecimal.ZERO;
    }
}
