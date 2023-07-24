package com.seektop.fund.payment.kuaifutongpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.PaymentMerchantEnum;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 快付通支付接口
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.KUAIFUTONGPAY + "")
public class KuaiFuTongPayer implements GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<>();
        DataContentParms.put("mchId", merchantAccount.getMerchantCode());
        DataContentParms.put("mchOrderNo", req.getOrderId());
        DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString());
        DataContentParms.put("accountAttr", "0");
        DataContentParms.put("accountName", req.getName());
        DataContentParms.put("accountNo", req.getCardNo());
        DataContentParms.put("notifyUrl", merchantAccount.getNotifyUrl()+ merchantAccount.getMerchantId());
        DataContentParms.put("remark", "withdraw");
        DataContentParms.put("reqTime", DateUtils.format(req.getCreateDate() , DateUtils.YYYYMMDDHHMMSS));

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase());

        log.info("=========KuaiFuTongPayer_Transfer_params: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.WITHDRAW.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/agentpay/apply" , DataContentParms, requestHeader);
        log.info("=========KuaiFuTongPayer_Transfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(DataContentParms));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !"SUCCESS".equals(json.getString("retCode"))) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("retMsg"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("retMsg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("========KuaiFuTongPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("agentpayOrderId");//三方商户订单号
        if(StringUtils.isEmpty(orderid)){
            JSONObject json = JSON.parseObject(resMap.get("reqBody"));
            orderid = json.getString("agentpayOrderId");
        }
        if (StringUtils.isNotEmpty(orderid)) {
            return doTransferQuery(merchant, orderid);
        } else {
            return null;
        }
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("mchId", merchant.getMerchantCode());
        DataContentParms.put("agentpayOrderId", orderId);//三方商户订单号
        DataContentParms.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchant.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase());

        log.info("===========KuaiFuTongPayer_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.WITHDRAW_QUERY.getCode());
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/agentpay/query_order", DataContentParms, requestHeader);
        log.info("===========KuaiFuTongPayer_TransferQuery_resStr:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if ("SUCCESS".equals(json.getString("retCode"))) {
            notify.setAmount(json.getBigDecimal("amount").divide(BigDecimal.valueOf(100)));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(json.getString("mchOrderNo"));
            notify.setThirdOrderId(json.getString("agentpayOrderId"));
            if (json.getString("status").equals("2")) {//订状态:0-待处理,1-处理中,2-成功,3-失败
                notify.setStatus(0);
            } else if (json.getString("status").equals("3")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> DataContentParms = new HashMap<>();
        DataContentParms.put("mchId", merchantAccount.getMerchantCode());
        DataContentParms.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS));

        String toSign = MD5.toAscii(DataContentParms) + "&key=" + merchantAccount.getPrivateKey();
        DataContentParms.put("sign", MD5.md5(toSign).toUpperCase());

        log.info("=========KuaiFuTongPayer_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","","", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/agentpay/query_balance", DataContentParms, requestHeader);
        log.info("==========KuaiFuTongPayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json != null && "SUCCESS".equals(json.getString("retCode"))) {
            return json.getBigDecimal("availableAgentpayBalance") == null ? BigDecimal.ZERO : json.getBigDecimal("availableAgentpayBalance");
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
                .channelId(PaymentMerchantEnum.KUAIFUTONG_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.KUAIFUTONG_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
