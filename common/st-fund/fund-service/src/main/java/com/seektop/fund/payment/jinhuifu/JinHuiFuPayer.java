package com.seektop.fund.payment.jinhuifu;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 金汇富支付
 */

@Slf4j
@Service(FundConstant.PaymentChannel.JINHUIFU + "")
public class JinHuiFuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    private static final String SERVER_PAY_URL = "/v4/pay";

    private static final String SERVER_QUERY_URL = "/v4/query";

    @Resource
    private GlRechargeBusiness rechargeBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        log.info("JinHuiFuPayer_recharge_GlPaymentMerchant_info:{}", JSONObject.toJSONString(merchant));
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId() || FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()
                || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()
                || FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() || FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            String payType;
            if(FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()
                    || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()){
                if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
                    payType = "3";
                } else {
                    payType = "4";
                }
            } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()
                    || FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
                if (merchant.getClientType() == ProjectConstant.ClientType.PC) {
                    payType = "1";
                } else {
                    payType = "2";
                }
            }else if(FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()){
                if(merchant.getClientType() == ProjectConstant.ClientType.PC){
                    payType = "5";
                }else{
                    payType = "6";
                }
            }else if(FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()){
                payType = "11";
            }else if(FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()){
                payType = "10";
            } else {
                payType = "5";
            }
            prepareToScan(merchant, account, req, result, payType);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> paramsMap = new TreeMap<>();
        paramsMap.put("amount", req.getAmount().toString());
        paramsMap.put("channel", payType);
        paramsMap.put("httpurl", account.getNotifyUrl() + merchant.getId());
        paramsMap.put("merchant_code", account.getMerchantCode());
        paramsMap.put("notifyurl", account.getNotifyUrl() + merchant.getId());
        paramsMap.put("orderid", req.getOrderId().toLowerCase());
        paramsMap.put("reference", "reference/attach");
        paramsMap.put("timestamp", (System.currentTimeMillis() / 1000) + "");
        String toSign = MD5.toAscii(paramsMap);
        toSign += "&" + account.getPrivateKey();
        paramsMap.put("sign", MD5.md5(toSign));
        log.info("JinHuiFuPayer_recharge_prepare_params:{}", JSON.toJSONString(paramsMap));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JINHUIFU.getCode() + "")
                .channelName(PaymentMerchantEnum.JINHUIFU.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_PAY_URL, paramsMap, requestHeader);
        log.info("JinHuiFuPayer_recharge_prepare_resp:{}", resStr);
        JSONObject json = JSONObject.parseObject(resStr);
        if (!json.getBoolean("status")) {
            throw new RuntimeException("创建订单失败");
        }
        JSONObject data = json.getJSONObject("data");
        result.setRedirectUrl(data.getString("return"));
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JinHuiFuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("order_id");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId.toUpperCase());
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        GlRecharge glRecharge = rechargeBusiness.findById(orderId);
        if (glRecharge != null) {
            Map<String, String> paramsMap = new TreeMap<>();
            paramsMap.put("merchant_code", account.getMerchantCode());
            paramsMap.put("orderid", orderId);
            paramsMap.put("amount", glRecharge.getAmount().toString());
            paramsMap.put("timestamp", (System.currentTimeMillis() / 1000) + "");
            String toSign = MD5.toAscii(paramsMap);
            toSign += "&" + account.getPrivateKey();
            paramsMap.put("sign", MD5.md5(toSign));
            log.info("JinHuiFuPayer_query_params:{}", JSON.toJSONString(paramsMap));
            GlRequestHeader requestHeader = GlRequestHeader.builder()
                    .action(GlActionEnum.RECHARGE_QUERY.getCode())
                    .channelId(PaymentMerchantEnum.JINHUIFU.getCode() + "")
                    .channelName(PaymentMerchantEnum.JINHUIFU.getPaymentName())
                    .userId("")
                    .userName("")
                    .tradeId(orderId.toUpperCase())
                    .build();
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVER_QUERY_URL, paramsMap, requestHeader);
            log.info("JinHuiFuPayer_query_resp:{}", resStr);
            JSONObject json = JSONObject.parseObject(resStr);
            if ("PAID".equals(json.getString("status")) || "SUCCESS".equals(json.getString("status"))) {
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(glRecharge.getAmount());
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId.toUpperCase());
                pay.setThirdOrderId(json.getString("transid"));
                return pay;
            }
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("merchant_code", merchantAccount.getMerchantCode());
        params.put("order_id", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        params.put("name", req.getName());
        params.put("bank", paymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        params.put("branch", "Shanghai");
        params.put("accountnumber", req.getCardNo());
        params.put("callback_url", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());
        params.put("timestamp",  (System.currentTimeMillis() / 1000)+"");

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        log.info("JinhuifuPayer_Transfer_params: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/withdraw" , params, requestHeader);
        log.info("JinhuifuPayer_Transfer_resStr: {}", resStr);


        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"true".equals(json.getString("status"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }

    //回调有两步  第一步是批准待出款  状态：APPROVED    第二步是已完成出款 状态：DISPENSED
    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("JinhuifuPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId = json.getString("order_id");
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (glWithdraw == null){
            return null;
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("merchant_code", merchant.getMerchantCode());
        params.put("order_id", orderId);

        String toSign = MD5.toAscii(params) + "&" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        log.info("JinhuifuPayer_TransferQuery_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/v4/merchant/withdraw/query", params, requestHeader);
        log.info("JinhuifuPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("true".equals(json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data");
            JSONObject dataJson = dataArr.getJSONObject(0);
            notify.setAmount(glWithdraw.getAmount().subtract(glWithdraw.getFee()));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            notify.setThirdOrderId("");
            //"REJECTED/APPROVED/PENDING/DISPENSED //拒绝/批准待出款/待支付/已完成出款    商户返回出款状态：0成功，1失败,2处理中
            if (dataJson.getString("status").equalsIgnoreCase("DISPENSED")) {
                notify.setStatus(0);
            } else if (dataJson.getString("status").equalsIgnoreCase("REJECTED")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("merchant_code", merchantAccount.getMerchantCode());
        params.put("time",  (System.currentTimeMillis() / 1000)+"");

        String toSign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        log.info("JinhuifuPayer_QueryBalance_reqMap: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/v4/merchant/balance", params, requestHeader);
        log.info("JinhuifuPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json != null && "true".equals(json.getString("status"))) {
            JSONArray dataArr = json.getJSONArray("data");
            JSONObject dataJson = dataArr.getJSONObject(0);
            if(dataJson != null){
                return dataJson.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJson.getBigDecimal("balance");
            }
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
                .channelId(PaymentMerchantEnum.JINHUIFU.getCode() + "")
                .channelName(PaymentMerchantEnum.ZHONGQIFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
