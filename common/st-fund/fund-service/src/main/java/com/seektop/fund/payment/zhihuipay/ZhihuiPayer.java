package com.seektop.fund.payment.zhihuipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.ZHIHUIFU + "")
public class ZhihuiPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        String key = account.getPrivateKey();

        Map<String, String> param = new LinkedHashMap<>();
        param.put("pay_memberid", account.getMerchantCode());
        param.put("pay_orderid", req.getOrderId());
        param.put("pay_applydate", DateUtils.getStrCurDate(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS));
        if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            param.put("pay_bankcode", "907");
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()
                || FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            param.put("pay_bankcode", "912");
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            param.put("pay_bankcode", "913");
        }
        param.put("pay_notifyurl", account.getNotifyUrl() + merchant.getId());
        param.put("pay_callbackurl", account.getResultUrl() + merchant.getId());
        param.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());

        String toSign = MD5.toAscii(param) + "&key=" + key;
        log.info("zhihuifu_PrepareToWangyin_toSign:{}", toSign);
        param.put("pay_md5sign", MD5.md5(toSign).toUpperCase());
        param.put("pay_productname", "CZ");
        log.info("ZhihuiPayer_PrepareToWangyin_paramMap:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.ZHIHUI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHIHUI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String formMessage = okHttpUtil.post(account.getPayUrl() + "/Pay_Index.html", param, requestHeader);
        log.info("ZhihuiPayer_PrepareToWangyin_resStr:{}", formMessage);
        result.setMessage(formMessage);

    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("ZhihuiPayer_notify_resMap:{}", JSON.toJSONString(resMap));
        if (!"00".equals(resMap.get("returncode"))) {
            return null;
        }
        String orderId = resMap.get("orderid");
        return query(account, orderId);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> param = new LinkedHashMap<>();
        param.put("pay_memberid", account.getMerchantCode());
        param.put("pay_orderid", orderId);
        String toSign = MD5.toAscii(param) + "&key=" + account.getPrivateKey();
        param.put("pay_md5sign", MD5.md5(toSign).toUpperCase());
        log.info("zhihuifu_Query_paramMap:{}", JSON.toJSONString(param));

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.ZHIHUI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHIHUI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String retBack = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", param, requestHeader);
        log.info("zhihuifu_Query_resStr: {}", retBack);
        JSONObject json = JSON.parseObject(retBack);
        if (json == null || json.getString("returncode") == null) {
            return null;
        }
        String respCode = json.getString("returncode");
        if (!"00".equals(respCode)) {
            return null;
        }
        if (!json.getString("trade_state").equals("SUCCESS")) {
            return null;
        }
        RechargeNotify notify = new RechargeNotify();
        notify.setAmount(json.getBigDecimal("amount"));
        notify.setFee(BigDecimal.ZERO);
        notify.setOrderId(orderId);
        notify.setThirdOrderId(json.getString("transaction_id"));
        return notify;
    }

    // 查询银行卡
    private boolean queryBankCard(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) {
        try {
            String arr[] = merchantAccount.getPrivateKey().split("\\|\\|");
            if (arr == null || arr.length != 2) {
                return false;
            }
            String toSign = "&memberid=" + merchantAccount.getMerchantCode() + "&banknumber=" + req.getCardNo();

            Map<String, String> param = new LinkedHashMap<>();
            String sign = AESUtils.encrypt(toSign, merchantAccount.getMerchantCode(), arr[1]);//报备私钥

            param.put("memberid", merchantAccount.getMerchantCode());
            param.put("banknumber", req.getCardNo());
            param.put("param", sign);

            log.info("ZhihuiPayer_queryBankCardReport_param:{}", JSON.toJSONString(param));

            String url = "http://cardreport.zpay.info/index.php?route=df/api/queryCard";
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), "queryBank");
            String resStr = okHttpUtil.post(url, param, requestHeader);

            log.info("ZhihuiPayer_queryBankCardReport_resStr:{}", resStr);

            if (ObjectUtils.isEmpty(resStr)) {
                return false;
            }
            JSONObject json = JSON.parseObject(resStr);

            if (!"2".equals(json.getString("status"))) {
                return false;
            }
            JSONObject card = json.getJSONObject("card");
            if (null != card && req.getCardNo().equals(card.getString("banknumber"))) {// 查询到已经上报直接返回
                return true;
            }
        } catch (Exception e) {
            log.info("ZhihuiPayer_queryBankCard_error:{}", e);
        }
        return false;
    }

    // 添加银行卡报备
    private boolean addBankCard(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) {
        try {
            String arr[] = merchantAccount.getPrivateKey().split("\\|\\|");
            if (arr == null || arr.length != 2) {
                return false;
            }
            Map<String, String> param = new LinkedHashMap<>();
            param.put("memberid", merchantAccount.getMerchantCode());

            StringBuffer toSign = new StringBuffer();
            toSign.append("&memberid=").append(merchantAccount.getMerchantCode());
            toSign.append("&bankname=").append(req.getBankName());
            toSign.append("&bankfullname=").append(req.getName());
            toSign.append("&banknumber=").append(req.getCardNo());
            toSign.append("&shen=");
            toSign.append("&shi=");

            log.info("ZhihuiPayer_addBankCard_toSign:{}", toSign.toString());
            String sign = AESUtils.encrypt(toSign.toString(), merchantAccount.getMerchantCode(), arr[1]);//报备私钥
            param.put("param", sign);

            log.info("ZhihuiPayer_addBankCardReport_param:{}", JSON.toJSONString(param));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), "addBank");
            String url = "http://cardreport.zpay.info/index.php?route=df/api/addCard";
            String resStr = okHttpUtil.post(url, param, requestHeader);
            log.info("ZhihuiPayer_addBankCardReport_resStr:{}", resStr);

            if (StringUtils.isEmpty(resStr)) {
                return false;
            }

            JSONObject json = JSON.parseObject(resStr);
            if (null == json) {
                return false;
            }

            if ("2".equals(json.getString("status"))) {
                return true;
            }
        } catch (Exception e) {
            log.info("ZhihuiPayer_addBankCard_error:{}", e);
        }
        return false;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        String arr[] = merchantAccount.getPrivateKey().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户配置密钥错误");
        }
        // 银行卡报备
        boolean isReportSuccess = true;
        if (!queryBankCard(merchantAccount, req)) {
            isReportSuccess = addBankCard(merchantAccount, req);
        }

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        if (!isReportSuccess) {
            result.setValid(false);
            result.setMessage("银行卡报备请求失败");
            return result;
        }

        String keyValue = arr[0];
        String paypassward = merchantAccount.getPublicKey();
        Map<String, String> param = new LinkedHashMap<>();
        param.put("mchid", merchantAccount.getMerchantCode());
        param.put("out_trade_no", req.getOrderId());
        param.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        param.put("bankname", req.getBankName());
        param.put("subbranch", "上海市");
        param.put("accountname", req.getName());
        param.put("cardnumber", req.getCardNo());
        param.put("province", "上海市");
        param.put("city", "上海市");
        param.put("paypassword", paypassward);//支付密码
        param.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

        String signStrTemp = MD5.toAscii(param) + "&key=" + keyValue;

        param.put("pay_md5sign", MD5.md5(signStrTemp).toUpperCase());

        log.info("ZhihuiPayer_Transfer_data:{}", JSON.toJSONString(param));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfapi_add.html", param, requestHeader);
        log.info("ZhihuiPayer_Transfer_response: {}", retBack);
        result.setReqData(JSON.toJSONString(param));
        result.setResData(retBack);
        if (StringUtils.isEmpty(retBack)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(retBack);
        if (json == null) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        if ("success".equals(json.getString("status")) && StringUtils.isNotEmpty(json.getString("transaction_id"))) {
            result.setValid(true);
            result.setMessage(json.getString("msg"));
        } else {
            result.setValid(false);
            result.setMessage(json.getString("msg"));
        }
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("zhihuifu_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        if (StringUtils.isNotEmpty(resMap.get("returncode")) && "00".equals(resMap.get("returncode")) &&
                StringUtils.isNotEmpty(resMap.get("transaction_id"))) {
//            return doTransferQuery(merchant, resMap.get("orderid"));
            String arr[] = merchant.getPrivateKey().split("\\|\\|");
            String sign = resMap.get("sign");
            String keyValue = arr[0];
            resMap.remove("sign");
            String toSign = MD5.toAscii(resMap) + "&key=" + keyValue;
            log.info("ZhihuiPayer_Transfer_notify_toSign:{}", toSign);
            if (MD5.md5(toSign).toUpperCase().equals(sign)) {
                WithdrawNotify notify = new WithdrawNotify();
                notify.setMerchantCode(merchant.getMerchantCode());
                notify.setMerchantId(merchant.getMerchantId());
                notify.setMerchantName(merchant.getChannelName());
                notify.setOrderId(resMap.get("orderid"));
                notify.setStatus(0);
                return notify;
            }
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        String arr[] = merchant.getPrivateKey().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            throw new RuntimeException("商户配置密钥错误");
        }
        Map<String, String> param = new LinkedHashMap<>();
        String keyValue = arr[0];// 代付私钥
        log.info("ZhihuiPayer_TransferQuery_OrderId:{}", orderId);
        param.put("mchid", merchant.getMerchantCode());
        param.put("out_trade_no", orderId);
        String signStrTemp = MD5.toAscii(param) + "&key=" + keyValue;
        param.put("pay_md5sign", MD5.md5(signStrTemp).toUpperCase());
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String retBack = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfapi_query.html", param, requestHeader);

        log.info("zhihuiPayer_TransferQuery_OrderId:{},response: {}", orderId, retBack);
        JSONObject json = JSONObject.parseObject(retBack);
        if (StringUtils.isEmpty(retBack) || json == null) {
            return null;
        }

        if (!"success".equals(json.getString("status"))) {
            return null;
        }

        String refCode = json.getString("refCode");
        WithdrawNotify notify = new WithdrawNotify();
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        if (refCode.equals("1")) {
            notify.setStatus(0);
        } else if (refCode.equals("2")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }

        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        String arr[] = merchantAccount.getPrivateKey().split("\\|\\|");
        if (arr == null || arr.length != 2) {
            return BigDecimal.ZERO;
        }
        String privateKey = arr[0];// 代付私钥;
        Map<String, String> param = new HashMap<>();
        param.put("mchid", merchantAccount.getMerchantCode());
        String sign = MD5.md5("mchid=" + merchantAccount.getMerchantCode() + "&key=" + privateKey).toUpperCase();
        param.put("pay_md5sign", sign);

        GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfapi_queryBalance.html", param, requestHeader);
        log.info("ZhihuiPayer_QueryBalance_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (null == json) {
            return BigDecimal.ZERO;
        }

        String status = json.getString("status");
        if (StringUtils.isNotEmpty(status) && "success".equals(status)) {
            return json.getBigDecimal("balance");
        }
        return BigDecimal.ZERO;
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
                .channelId(PaymentMerchantEnum.ZHIHUI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHIHUI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }

}
