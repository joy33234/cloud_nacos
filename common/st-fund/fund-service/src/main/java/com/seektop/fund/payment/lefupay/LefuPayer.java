package com.seektop.fund.payment.lefupay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.google.protobuf.ServiceException;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import sun.misc.BASE64Decoder;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 乐付支付
 *
 */
@Slf4j
@Service(FundConstant.PaymentChannel.LEFUPAY + "")
public class LefuPayer implements GlPaymentRechargeHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlRechargeMapper rechargeMapper;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        String service = "";
        if(merchant.getPaymentId() == FundConstant.PaymentType.UNION_PAY){
            service = "UnionQrcodeOrder";
        }
        if(StringUtils.isNotEmpty(service)){
            prepareScan(merchant, payment, req, result,service);
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            String arr[] = payment.getMerchantCode().split("\\|\\|");
            if (arr == null || arr.length != 2) {
                throw new RuntimeException("商户未配置机构号-创建订单失败");
            }

            RSAPrivateKey privateKey = loadPrivateKey(payment.getPublicKey());

            /**
             * 业务数据加密
             */
            Map<String, String> data = new HashMap<>();
            data.put("linkId", req.getOrderId());
            data.put("orderType", "10");
            data.put("goodsName", "recharge");
            data.put("amount", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
            data.put("notifyUrl", payment.getNotifyUrl()+ merchant.getId());
            data.put("frontUrl", payment.getResultUrl()+ merchant.getId());
            log.info("===========LefuPayer_Prepare_Params_Data:{}", JSON.toJSONString(data));
            //请求参数
            Map<String, String> params = Maps.newHashMap();
            params.put("orgNo", arr[1]);
            params.put("merNo", arr[0]);
            params.put("action", service);

            String encryptkey = UUID.randomUUID().toString().substring(0,16);
            String dataStr = AESTool.encrypt(JSON.toJSONString(data), encryptkey);
            params.put("data", dataStr);

            String rsaEncryptkey = Base64.encode(RSATool.encrypt(encryptkey.getBytes("UTF-8"), privateKey));
            params.put("encryptkey", rsaEncryptkey);

            //数据签名
            StringBuilder signBuffer = new StringBuilder();
            signBuffer.append(arr[1]);
            signBuffer.append(arr[0]);
            signBuffer.append(service);
            signBuffer.append(dataStr);
            String sign = SignUtils.sign(signBuffer.toString(), payment.getPrivateKey());
            params.put("sign", sign);

            log.info("===========LefuPayer_Prepare_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId()+"",req.getUsername(),req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String restr = okHttpUtil.post(payment.getPayUrl() + "/sdk/action" ,params,requestHeader);
            log.info("===========LefuPayer_Prepare_resStr:{}", restr);


            JSONObject jsonObject = JSON.parseObject(restr);
            String rtnData = jsonObject.getString("data");
            String rtnEncryptkey = jsonObject.getString("encryptkey");
            if (StringUtils.isEmpty(rtnData) || StringUtils.isEmpty(rtnEncryptkey)) {
                throw new ServiceException("创建订单失败:解密异常");
            }

            byte[] aesKey = RSATool.decrypt(Base64.decode(rtnEncryptkey), privateKey);
            String decryptStr = AESTool.decrypt(rtnData, new String(aesKey));
            log.info("===========LefuPayer_Prepare_resStr2:{}",decryptStr);

            JSONObject dataJSON = JSON.parseObject(decryptStr);

            if(dataJSON == null || !dataJSON.getString("code").equals("000000") || StringUtils.isEmpty(dataJSON.getString("payUrl"))){
                throw new ServiceException("创建订单失败:返回数据异常");
            }
            result.setRedirectUrl(dataJSON.getString("payUrl"));
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
        log.info("========LefuPayer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("linkId");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        try {
            String arr[] = account.getMerchantCode().split("\\|\\|");
            if (arr == null || arr.length != 2) {
                throw new RuntimeException("商户未配置机构号-创建订单失败");
            }

            RSAPrivateKey privateKey = loadPrivateKey(account.getPublicKey());

            /**
             * 业务数据加密
             */
            Map<String, String> data = new HashMap<>();
            data.put("orderNo", "");
            data.put("linkId", orderId);


            String encryptkey = UUID.randomUUID().toString().substring(0,16);
            String dataStr = AESTool.encrypt(JSON.toJSONString(data), encryptkey);
            /**
             * 数据签名
             */
            StringBuilder signBuffer = new StringBuilder();
            signBuffer.append(arr[1]);
            signBuffer.append(arr[0]);
            signBuffer.append("OrderStatus");
            signBuffer.append(dataStr);
            String sign = SignUtils.sign(signBuffer.toString(), account.getPrivateKey());

            Map<String, String> params = Maps.newHashMap();
            params.put("orgNo", arr[1]);
            params.put("merNo", arr[0]);
            params.put("action", "OrderStatus");
            params.put("data", dataStr);
            String rsaEncryptkey = Base64.encode(RSATool.encrypt(encryptkey.getBytes("UTF-8"), privateKey));

            params.put("encryptkey", rsaEncryptkey);
            params.put("sign", sign);

            log.info("===========LefuPayer_Query_Params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
            String restr = okHttpUtil.post(account.getPayUrl() + "/sdk/action" ,params,requestHeader);
            log.info("===========LefuPayer_Query_resStr:{}", restr);


            JSONObject jsonObject = JSON.parseObject(restr);
            String rtnData = jsonObject.getString("data");
            String rtnEncryptkey = jsonObject.getString("encryptkey");
            if (StringUtils.isEmpty(rtnData) || StringUtils.isEmpty(rtnEncryptkey)) {
                return null;
            }

            byte[] aesKey = RSATool.decrypt(Base64.decode(rtnEncryptkey), privateKey);
            String decryptStr = AESTool.decrypt(rtnData, new String(aesKey));
            log.info("===========LefuPayer_Query_decryptStr2:{}",decryptStr);
            JSONObject json = JSON.parseObject(decryptStr);
            if (json == null) {
                return null;
            }
            if ("000000".equals(json.getString("code")) && "20".equals(json.getString("orderStatus"))) {// 20	支付成功	终态
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(json.getBigDecimal("orderAmount").divide(new BigDecimal(100)).setScale(0, RoundingMode.DOWN));
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId);
                pay.setThirdOrderId(json.getString("orderNo"));
                log.info("pay:{}", JSON.toJSONString(pay));
                return pay;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                .channelId(PaymentMerchantEnum.LEFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.LEFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }




    private static RSAPrivateKey loadPrivateKey(String privateKeyStr) throws Exception {
        BASE64Decoder base64Decoder = new BASE64Decoder();
        byte[] buffer = base64Decoder.decodeBuffer(privateKeyStr);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(buffer);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

}
