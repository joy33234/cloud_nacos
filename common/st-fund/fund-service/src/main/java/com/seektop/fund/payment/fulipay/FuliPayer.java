package com.seektop.fund.payment.fulipay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.mapper.GlWithdrawMapper;
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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * 富力支付
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.FULIPAY + "")
public class FuliPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("requestId", req.getOrderId());
            params.put("merchantCode", payment.getMerchantCode());
            params.put("totalBizType", "BIZ01100");
            params.put("totalPrice", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
                params.put("bankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()));
            }
            params.put("backurl", payment.getNotifyUrl() + merchant.getId());
            params.put("returnurl", payment.getNotifyUrl() + merchant.getId());
            params.put("noticeurl", payment.getNotifyUrl() + merchant.getId());
            params.put("description", "CZ");
            params.put("payType", "25");

            String toSign = req.getOrderId() + payment.getMerchantCode() + params.get("totalBizType") + params.get("totalPrice")
                    + params.get("backurl") + params.get("returnurl") + params.get("noticeurl") + params.get("description");
            params.put("mersignature", SignatureUtil.hmacSign(toSign, payment.getPrivateKey()));

            params.put("productId", "1");
            params.put("productName", "CZ");
            params.put("fund", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
            params.put("merAcct", payment.getMerchantCode());
            params.put("bizType", "BIZ01100");
            params.put("productNumber", "1");

            log.info("FuliPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/simplepay/pay_mobilePay", params, requestHeader);
            log.info("FuliPayer_Prepare_resStr:{}", restr);

            if (StringUtils.isEmpty(restr)) {
                throw new GlobalException("创建订单失败");
            }

            result.setMessage(restr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        log.info("FuLiPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("requestId");
        } else {
            orderId = json.getString("requestId");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.query(payment, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("requestId", orderId + System.currentTimeMillis());
        params.put("originalRequestId", orderId);
        params.put("merchantCode", account.getMerchantCode());

        String toSign = params.get("requestId") + account.getMerchantCode() + params.get("originalRequestId");
        params.put("signature", SignatureUtil.hmacSign(toSign, account.getPrivateKey()));

        log.info("FuLiPayer_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/main/SearchOrderAction_merSingleQuery", params, requestHeader);
        log.info("FuLiPayer_Query_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("00000".equals(json.getString("result")) && "2".equals(json.getString("status"))) {// 0：待处理（通过校验的初始状态） 1：处理中（支付执行中）2：成功（支付成功）3：失败（支付失败） 4：待确认
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("tradeSum").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("tradeId"));
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
        return notify(merchant, payment, resMap);
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requestId", req.getOrderId());
        params.put("merchantCode", merchantAccount.getMerchantCode());
        params.put("transferType", "1");
        params.put("sum", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        params.put("unionBankNum", "1");//联行号
        params.put("accountType", "1");//0：普通，1：快速
        params.put("branchBankName", "Shanghai");
        params.put("openBankName", "Shanghai");
        params.put("openBankProvince", "Shanghai");
        params.put("openBankCity", "Shanghai");
        params.put("accountName", req.getName());
        params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("bankAccount", req.getCardNo());
        params.put("reason", "withdraw");
        params.put("noticeUrl", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());
        params.put("refundNoticeUrl", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());

        String toSign = req.getOrderId() + merchantAccount.getMerchantCode() + params.get("transferType") + params.get("sum")
                + params.get("accountType") + params.get("unionBankNum") + params.get("branchBankName") + params.get("openBankName")
                + params.get("openBankProvince") + params.get("openBankCity") + req.getName() + params.get("bankCode")
                + params.get("bankAccount") + params.get("reason") + params.get("noticeUrl") + params.get("refundNoticeUrl");
        params.put("signature", SignatureUtil.hmacSign(toSign, merchantAccount.getPrivateKey()));

        log.info("FuLiPayer_Transfer_params: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        Charset charset = Charset.forName("GBK");
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/main/singleTransfer_toTransfer" , params, requestHeader,null,charset);
        log.info("FuLiPayer_Transfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"00000".equals(json.getString("result"))) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }


    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("FuLiPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("requestId");
        } else {
            orderId = json.getString("requestId");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return doTransferQuery(merchant, orderId);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("merchantCode", merchant.getMerchantCode());
        params.put("originalRequestId", orderId);
        params.put("requestId", (orderId + System.currentTimeMillis()));

        String toSign = params.get("requestId") + merchant.getMerchantCode() + params.get("originalRequestId");
        params.put("signature", SignatureUtil.hmacSign(toSign, merchant.getPrivateKey()));

        log.info("FuLiPayer_TransferQuery_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/main/singleTransfer_singleTransferQuery", params, requestHeader);
        log.info("FuLiPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("00000".equals(json.getString("result"))) {
            GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
            if(glWithdraw == null) {
                return null;
            }
            notify.setAmount(glWithdraw.getAmount().subtract(glWithdraw.getFee()));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            notify.setThirdOrderId("");
            //0：待系统自动复核（通过校验的初始状态）
            //1：已接收（付款复核通过）
            //2：成功（付款现成功）
            //3：失败（付款失败）
            //4：复核拒绝（付款复核拒绝）
            //9：已请求（渠道同步返回受理）
            //10：已退票（成功付款交易改为失败）
            if (json.getString("status").equals("2")) {//    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0);
            } else if (json.getString("status").equals("3") || json.getString("status").equals("10")) {
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
        params.put("merchantCode", merchantAccount.getMerchantCode());
        params.put("requestId", System.currentTimeMillis()+"");

        String toSign = params.get("requestId") + merchantAccount.getMerchantCode() ;
        params.put("signature", SignatureUtil.hmacSign(toSign, merchantAccount.getPrivateKey()));

        log.info("FuLiPayer_QueryBalance_reqMap: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/main/MerchantAccountQueryAction_merchantAccountQuery", params, requestHeader);
        log.info("FuLiPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json != null && "00000".equals(json.getString("result"))) {
            return json.getBigDecimal("balance") == null ? BigDecimal.ZERO : json.getBigDecimal("balance");
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
                .channelId(PaymentMerchantEnum.FULI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FULI_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
