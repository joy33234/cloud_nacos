package com.seektop.fund.payment.ipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.rest.Result
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
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * @desc ipay支付
 * @auth joy
 * @date 2021-05-02
 */

public class IpayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(IpayScript_recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult


        Map<String, String> params = new TreeMap<>()
        params.put("amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN) + "")
        params.put("clientip", req.getIp())
        params.put("currency", "CNY")
        params.put("mhtorderno", req.getOrderId())
        params.put("mhtuserid", req.getUserId().toString())
        params.put("notifyurl", account.getNotifyUrl() + merchant.getId())
        params.put("opmhtid", account.getMerchantCode())
        params.put("payername", req.getFromCardUserName())
        params.put("paytype", "bank")
        params.put("random", req.getOrderId())

        params.put("sign", getSign(account.getPrivateKey(), MD5.toAscii(params)))
        log.info("IpayScript_recharge_prepare_params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String payUrl = account.getPayUrl() + "/api/v2/pay/placeOrder"
        String resStr = okHttpUtil.post(payUrl, params, 60L, requestHeader)
        log.info("IpayScript_recharge_prepare_resp = {}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSON.parseObject(resStr)
        if ("0" != (json.getString("rtCode")) ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("result")
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("payurl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("payurl"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("IpayScript_notify_resp:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("mhtorderno")
        String thirdOrderId = resMap.get("pforderno")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, thirdOrderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mhtorderno", orderId)
        params.put("opmhtid", account.getMerchantCode())
        params.put("random", orderId)
        params.put("sign", getSign(account.getPrivateKey(), MD5.toAscii(params)))

        log.info("IpayScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())

        String queryUrl = account.getPayUrl() + "/api/v2/pay/getInfo"
//        String resStr = okHttpUtil.get(queryUrl, params,  requestHeader)
        String resStr = doGet(queryUrl, params)

        log.info("IpayScript_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "0" != (json.getString("rtCode"))) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("result")
        //  0：未付款  1：已付款   2：超时   3：付款失败
        if (dataJSON != null && "1" == (dataJSON.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("paidamount").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(thirdOrderId)
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

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

    private  String getSign(String privatekey, String content) throws  NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec signingKey = new SecretKeySpec(privatekey.getBytes(), HMAC_SHA1_ALGORITHM);
        final Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        return  bytesToHex(mac.doFinal(content.getBytes()));

    }

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA384";

    private static String bytesToHex(final byte[] hash) {
        final StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            final String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    private static String doGet(String url, Map<String, String> params) {
        StringBuilder urlTmp = new StringBuilder(url);
        urlTmp.append("?");
        Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            try {
                urlTmp.append(entry.getKey()).append("=").append(java.net.URLEncoder.encode(String.valueOf(entry.getValue()), "utf-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return JSON.toJSONString(Result.newBuilder().setMessage("URL构建异常!").fail().build());
            }
            urlTmp.append("&");
        }
        return doGet(urlTmp.toString());
    }

    private static   String doGet(String url) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
            }
        }
        return null;
    }

}