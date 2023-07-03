package com.seektop.fund.payment.colaPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.Security

/**
 * @desc 可乐支付
 * @date 2021-10-27
 * @auth otto
 */
public class ColaScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(ColaScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private final String PAY_URL = "/index/unifiedorder?format=json"

    private final String QUERY_URL = "/index/getorder"

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String gateway = ""
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "cardtocard" //卡转卡
        }

        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, Object> params = new LinkedHashMap<>()
        params.put("appid", account.getMerchantCode())
        params.put("pay_type", gateway) //通道编码
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("callback_url", account.getNotifyUrl() + merchant.getId())
        params.put("success_url", account.getNotifyUrl() )
        params.put("error_url", account.getNotifyUrl() )
        params.put("out_uid", req.getUserId()+"")
        params.put("out_trade_no", req.getOrderId())
        params.put("version", "v1.1")

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        String data_sign = MD5.md5(toSign).toUpperCase()
        params.put("sign", data_sign)
        params.put("ip", req.getIp())
        params.put("pay_productname", "recharge")
        params.put("format", "json")

        log.info("ColaScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("ColaScript_recharge_prepare_resp:{} , orderId: {}", resStr , req.getOrderId() )

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("api接口异常，稍后重试")
            return
        }

        if (200 != json.getInteger("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        if (StringUtils.isEmpty(json.getString("url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }

        result.setRedirectUrl(json.getString("url"))
        result.setThirdOrderId("")

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("ColaScript_notify_resp:{}", JSON.toJSONString(resMap))

        String orderId = resMap.get("coke_out_trade_no") ;
        String coke_data = parseBase64( resMap.get("coke_data")) ;
        String content = decodeByAES( coke_data , account.getPrivateKey() ,orderId );

        //解密出来的单号 等于 传送回来的单号
        if (orderId == JSON.parseObject(content).getString("coke_out_trade_no")  ) {
            return this.payQuery(okHttpUtil, account, orderId)
        }

        log.info("ColaScript_notify_Sign: 回调资料错误或验签失败，单号：{}", orderId )
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("appid", account.getMerchantCode())
        params.put("out_trade_no", orderId)

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey()
        params.put("sign", MD5.md5(toSign).toUpperCase())

        //进程已结束，退出代码为 0
        log.info("ColaScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("ColaScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "200" != json.getString("code")) {
            return null
        }

        // 2:未支付 3:订单超时 4:支付 完成
        JSONObject dataJson = json.getJSONArray("data").get(0);
        if ( 4 == (dataJson.getInteger("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("money").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("success")
            return pay

        }

        return null
    }

    void cancel(Object[] args) throws GlobalException {

    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }

    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        return FundConstant.ShowType.NORMAL
    }

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

    // 解密
    public static String parseBase64(String s) {
        byte[] b;
        String result = null;
        if (s != null) {
            Base64.Decoder decoder = Base64.getDecoder();
            try {
                b = decoder.decode(s);
                result = new String(b, StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static String decodeByAES(String content, String aes_key , String orderId ) throws Exception {
        try {
            //导入支持AES/CBC/PKCS7Padding的Provider
            Security.addProvider(new BouncyCastleProvider());

            byte[] raw = aes_key.getBytes("UTF-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = Base64.getDecoder().decode(content);//先用base64解密
            try {
                byte[] original = cipher.doFinal(encrypted1);
                String originalString = new String(original, "UTF-8");
                return originalString;

            } catch (Exception e) {
                log.info("ColaScript_notify_Sign: AES解析失败，单号：{}", orderId)
                return null;
            }
        } catch (Exception e) {
            throw new Exception("AES解密失败：" + e.getMessage(), e);
        }
    }

}