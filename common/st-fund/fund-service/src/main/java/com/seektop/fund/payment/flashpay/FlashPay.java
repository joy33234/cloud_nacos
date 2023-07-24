package com.seektop.fund.payment.flashpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.PaymentMerchantEnum;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service(FundConstant.PaymentChannel.FLASHPAY + "")
public class FlashPay implements GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw withdraw) throws GlobalException {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", withdraw.getAmount().subtract(withdraw.getFee()).setScale(2, RoundingMode.DOWN));
        params.put("app_id", merchantAccount.getMerchantCode());
        params.put("merchant_order_id", withdraw.getOrderId());
        params.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("receive_bank_name", withdraw.getBankName());
        params.put("receive_card_holder", withdraw.getName());
        params.put("receive_card_number", withdraw.getCardNo());
        params.put("receive_sub_bank_name", "上海市");

        String toSign = this.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("FlashPay_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId(withdraw.getUserId() + "")
                .userName(withdraw.getUsername())
                .tradeId(withdraw.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/create_withdraw", JSON.toJSONString(params), requestHeader);
        log.info("FlashPay_doTransfer_resp:{}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(withdraw.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        if (!json.getString("status").equals("1")) {
            result.setValid(false);
            result.setMessage(json.getString("message"));
            return result;
        }
        result.setValid(true);
        result.setMessage("");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("FlashPay_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        if (json != null && StringUtils.isNotEmpty(json.getString("merchant_order_id"))) {
            return this.doTransferQuery(merchant, json.getString("merchant_order_id"));
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, Object> params = new HashMap<>();
        params.put("app_id", merchant.getMerchantCode());
        params.put("merchant_order_id", orderId);

        String toSign = this.toAscii(params) + "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        log.info("FlashPay_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/query_withdraw", JSON.toJSONString(params), requestHeader);
        log.info("FlashPay_doTransferQuery_resp:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setThirdOrderId("");
        if (json.getString("status").equals("3")) {//订单状态判断标准： 1 等待处理 2 处理中  3 成功 4 失败 5 成功但已返款  商户返回出款状态：0成功，1失败,2处理中
            notify.setStatus(0);
        } else if (json.getString("status").equals("4")) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, Object> params = new HashMap<>();
        params.put("app_id", merchantAccount.getMerchantCode());
        params.put("random_str", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS).substring(8));
        String toSign = this.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        log.info("FlashPay_QueryBalance_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.FLASH_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FLASH_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build();
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/query_balance", JSON.toJSONString(params), requestHeader);
        log.info("FlashPay_QueryBalance_resStr:{}", resStr);
        if (null == resStr) {
            return BigDecimal.ZERO;
        }
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return BigDecimal.ZERO;
        }
        return json.getBigDecimal("balance");
    }


    /**
     * ASCII排序
     *
     * @param parameters
     * @return
     */
    public String toAscii(Map<String, Object> parameters) {
        List<Map.Entry<String, Object>> infoIds = new ArrayList<>(parameters.entrySet());
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing(o -> (o.getKey())));
        StringBuffer sign = new StringBuffer();
        for (Map.Entry<String, Object> item : infoIds) {
            String k = item.getKey();
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue();
                if (null != v && !ObjectUtils.isEmpty(v)) {
                    sign.append(k + "=" + v + "&");
                }
            }
        }
        return sign.deleteCharAt(sign.length() - 1).toString();
    }
}
