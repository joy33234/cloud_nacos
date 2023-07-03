package com.seektop.fund.payment.tjpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
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
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service(FundConstant.PaymentChannel.TJPAY + "")
public class TJPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        String url = account.getPayUrl() + "/api/order/pay"; //请求的URL地址

        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("sid", account.getMerchantCode());
        DataContentParms.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");
        DataContentParms.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
        DataContentParms.put("outTradeNo", req.getOrderId());
        DataContentParms.put("orderType", "2"); //订单类型，固定值为2
        DataContentParms.put("payType", "6032");

        String md5key = account.getPrivateKey();
        String sign = sign(DataContentParms, md5key);
        DataContentParms.put("sign", sign);
        log.info("===========TJPayer_Prepare_DataContentParms:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
        String restr = okHttpUtil.post(url, DataContentParms,requestHeader);
        log.info("===========TJPayer_Prepare_resStr:{}", restr);

        if(StringUtils.isEmpty(restr)){
            throw new GlobalException("创建订单失败");
        }
        JSONObject returnJson = JSON.parseObject(restr);
        if (returnJson != null && returnJson.getBoolean("result")) {
            //充值成功，处理业务请求
            JSONObject dataJson = returnJson.getJSONObject("data");
            result.setRedirectUrl(dataJson.getString("payUrl"));
        } else {
            //充值失败，处理业务请求
            throw new GlobalException("创建订单失败");
        }
    }

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return notify(merchant, account, resMap);
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("========TJPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("out_trade_no");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(account, orderid);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {

        String url = account.getPayUrl() + "/api/order/getByOutTradeNo";    //请求的URL地址
        String md5Key = account.getPrivateKey(); //秘钥

        Map<String, String> DataContentParms = new HashMap<String, String>();
        DataContentParms.put("outTradeNo", orderId);   //订单号
        DataContentParms.put("sid", account.getMerchantCode());   //商户号
        String sign = sign(DataContentParms, md5Key);   //签名
        DataContentParms.put("sign", sign);

        log.info("===========TJPayer_Query_reqMap:{}", JSON.toJSONString(DataContentParms));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(url, DataContentParms, requestHeader);
        log.info("===========TJPayer_Query_resStr:{}", resStr);

        JSONObject returnJson = JSON.parseObject(resStr);
        if (returnJson != null && returnJson.getBoolean("result")) {
            //查询成功，处理业务请求
            JSONObject dataJson = returnJson.getJSONObject("data");
            //status ： 10 待支付  11 未支付  12 支付成功
            if (dataJson != null && "12".equalsIgnoreCase(dataJson.getString("status"))) {
                //支付成功
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(BigDecimal.valueOf(Double.valueOf(dataJson.getString("amount"))).setScale(2, RoundingMode.DOWN));
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId);
                pay.setThirdOrderId("");
                return pay;
            }
        }
        return null;
    }


    /**
     * 签名方法
     *
     * @param DataContentParms 待签名参数map
     * @param md5key   密钥
     */
    private static String sign(Map<String, String> DataContentParms, String md5key) {
        if (DataContentParms == null || DataContentParms.isEmpty() || StringUtils.isBlank(md5key)) {
            return null;
        }
        Set<String> keyset = new TreeSet<>();
        keyset.addAll(DataContentParms.keySet());
        StringBuilder paramBuilder = new StringBuilder();
        keyset.forEach(key -> {
            if (StringUtils.isNotBlank(DataContentParms.get(key)) && !StringUtils.equals(key, "sign")) {
                paramBuilder.append("&").append(key).append("=").append(DataContentParms.get(key));
            }
        });
        return MD5Util.MD5Encode(paramBuilder.append("@@").append(md5key).substring(1), "UTF-8");
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
                .channelId(PaymentMerchantEnum.TJ_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.TJ_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}
