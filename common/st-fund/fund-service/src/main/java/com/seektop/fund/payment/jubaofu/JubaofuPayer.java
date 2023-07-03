package com.seektop.fund.payment.jubaofu;

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
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 聚宝付支付接口
 *
 * @author ab
 */
@Slf4j
@Service(FundConstant.PaymentChannel.JUBAOFU + "")
public class JubaofuPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

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
        String keyValue = payment.getPrivateKey();

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("customerno", payment.getMerchantCode());
        paramMap.put("channeltype", "onlinebank");
        if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "wechat_qrcode");
        } else if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "alipay_qrcode");
            if (req.getClientType() != 0) {
                paramMap.put("channeltype", "alipay_app");
            }
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            paramMap.put("channeltype", "yl_qrcode");
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "yl_nocard");
        } else if (FundConstant.PaymentType.JD_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "jd_qrcode");
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            paramMap.put("channeltype", "onlinebank");
            paramMap.put("bankcode", paymentChannelBankBusiness.getBankCode(req.getBankId(), merchant.getChannelId()));
        }
        paramMap.put("customerbillno", req.getOrderId());
        paramMap.put("orderamount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        paramMap.put("customerbilltime", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
        paramMap.put("notifyurl", payment.getNotifyUrl() + merchant.getId());
        paramMap.put("returnurl", payment.getResultUrl() + merchant.getId());
        paramMap.put("ip", req.getIp());
        paramMap.put("devicetype", "web");
        paramMap.put("customeruser", req.getUsername());

        String toSign = MD5.toAscii(paramMap);
        toSign = toSign + "&key=" + keyValue;
        log.info("JubaofuPayer_Prepare_toSign: {}", toSign);
        String sign = MD5.md5(toSign).toLowerCase();
        paramMap.put("sign", sign);

        log.info("JubaofuPayer_Prepare_paramMap: {}", JSON.toJSONString(paramMap));
        String resultStr = HtmlTemplateUtils.getPost(payment.getPayUrl() + "/api/gateway", paramMap);
        log.info("JubaofuPayer_Prepare_result: {}", JSON.toJSONString(resultStr));
        result.setMessage(resultStr);
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
        log.info("JubaofuPayer_Notify_resMap: {}", JSON.toJSONString(resMap));
        String customerbillno = resMap.get("customerbillno");
        String preorderamount = resMap.get("preorderamount");
        if (StringUtils.isEmpty(customerbillno) || StringUtils.isEmpty(preorderamount)) {
            return null;
        }
        return query(payment, customerbillno + "-" + preorderamount);
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        String[] order = orderId.split("-");
        Map<String, String> params = new HashMap<>();
        params.put("customerno", account.getMerchantCode());
        params.put("customerbillno", order[0]);
        params.put("orderamount", order[1]);
        log.info("JubaofuPayer_Query_params: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.JUBAOFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JUBAOFU_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String result = okHttpUtil.post(account.getPayUrl() + "/api/query", params, requestHeader);
        log.info("JubaofuPayer_Query_resStr: {}", result);
        if (StringUtils.isEmpty(result)) {
            return null;
        }
        JSONObject json = JSON.parseObject(result);
        if (json == null) {
            return null;
        }
        Boolean Result = json.getBoolean("Result");
        String PayStatus = json.getString("PayStatus");
        if (Result == true && "SUCCESS".equals(PayStatus)) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("OrderAmount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(order[0]);
            pay.setThirdOrderId(json.getString("OrderNo"));
            return pay;
        }
        return null;
    }

    /**
     * 解析页面跳转结果
     *
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, payment, resMap);
    }

}
