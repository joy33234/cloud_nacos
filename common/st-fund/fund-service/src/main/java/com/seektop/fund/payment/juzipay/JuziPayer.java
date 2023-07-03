package com.seektop.fund.payment.juzipay;

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
import org.eclipse.jetty.util.UrlEncoded;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


@Slf4j
@Service(FundConstant.PaymentChannel.JUZIPAY + "")
public class JuziPayer implements GlPaymentRechargeHandler {

    private static final String SERVER_PAY_QR_URL = "/api/v1/trades/v2page/";//扫码支付地址

    @Resource
    private OkHttpUtil okHttpUtil;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        Map<String, String> map = new TreeMap<>();
        map.put("app_user", req.getUsername());
        map.put("app_id", account.getMerchantCode());
        map.put("out_trade_sn", req.getOrderId());
        map.put("coin_type", "107");//币种类型  人民币
        map.put("quantity", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        map.put("account_type", "6");//支付宝综合
        map.put("sign", getSign(map, account.getPrivateKey()));//生成签名
        log.info("JuziPayer_recharge_prepare_params:{}", JSON.toJSONString(map));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.JUZI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JUZI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resStr = okHttpUtil.postJSON((account.getPayUrl() + SERVER_PAY_QR_URL), JSON.toJSONString(map));
        log.info("JUZI_PAY_recharge_prepare_resp:{}", resStr);
        JSONObject json = this.checkResponse(resStr);
        if (json == null) {
            throw new RuntimeException("创建订单失败");
        }
        String data = json.get("data").toString();
        JSONObject dataJson = JSON.parseObject(data);
        result.setRedirectUrl(dataJson.getString("cashier_url"));
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JUZI_notify_resp:{}", JSON.toJSONString(resMap));
        if (resMap == null) {
            return null;
        } else {
            JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
            log.info("JUZI_notify_json:{}", json.toJSONString());
            if (json != null) {
                String originalSign = json.getString("sign");
                String orderId = json.getString("out_trade_sn");
                String amount = json.getBigDecimal("quantity").toString();

                Map<String, String> map = new TreeMap<>();
                map.put("app_id", json.getString("app_id"));
                map.put("merchant_id", json.getString("merchant_id"));
                map.put("app_user", json.getString("app_user"));
                map.put("out_trade_sn", orderId);
                map.put("trade_type", json.getString("trade_type"));
                map.put("coin_type", json.getString("coin_type"));
                map.put("quantity", amount);
                map.put("status", json.getString("status"));
                String sign = this.getSign(map, account.getPrivateKey());
                log.info("JUZI_notify_sign:{}", sign);
                // 验证签名和状态
                if ("4".equals(json.getString("status")) && StringUtils.equals(originalSign, sign)) {//状态-3:失效-2:删除-1:取消0:创建1:未支付2:支付4:成功
                    RechargeNotify pay = new RechargeNotify();
                    pay.setAmount(new BigDecimal(amount));
                    pay.setFee(BigDecimal.ZERO);
                    pay.setOrderId(orderId);
                    pay.setThirdOrderId("");
                    return pay;
                }
            }
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return null;
    }

    //生成签名
    private String getSign(Map<String, String> map, String privateKey) {
        String sign = null;
        try {
            Map<String, String> params = paraFilter(map);
            StringBuilder buf = new StringBuilder((params.size() + 1) * 10);
            buildPayParams(buf, params, false);
            String preStr = buf.toString();
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            sha256_HMAC.init(new SecretKeySpec(privateKey.getBytes(), "HmacSHA256"));
            byte[] bytes = sha256_HMAC.doFinal(preStr.getBytes());
            sign = DatatypeConverter.printHexBinary(bytes).toLowerCase();
        } catch (Exception e) {
            log.error("橘子支付充值生成签名异常");
        }
        return sign;
    }

    /**
     * 检验返回数据
     *
     * @param response
     * @return
     */
    private JSONObject checkResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return null;
        }
        JSONObject json = JSON.parseObject(response);
        if (json == null || !"200".equals(json.getString("code"))) {
            return null;
        }
        return json;
    }

    //商户提供的生成加密方法
    private static Map<String, String> paraFilter(Map<String, String> sArray) {
        Map<String, String> result = new HashMap<String, String>(sArray.size());
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key);
            if (value == null || "".equals(value) || "sign".equalsIgnoreCase(key)) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    //商户提供的生成加密方法
    private static void buildPayParams(StringBuilder sb, Map<String, String> payParams, boolean encoding) {
        List<String> keys = new ArrayList<String>(payParams.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            sb.append(key).append("=");
            if (encoding) {
                sb.append(UrlEncoded.encodeString(payParams.get(key)));
            } else {
                sb.append(payParams.get(key));
            }
            sb.append("&");
        }
        sb.setLength(sb.length() - 1);
    }
}
