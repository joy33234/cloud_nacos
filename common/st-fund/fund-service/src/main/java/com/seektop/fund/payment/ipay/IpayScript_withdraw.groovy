package com.seektop.fund.payment.ipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.rest.Result
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

class IpayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(IpayScript_withdraw.class)

    private OkHttpUtil okHttpUtil
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("accno", req.getCardNo())
        paramMap.put("accname", req.getName())
        paramMap.put("acctype", "bank_card")
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("bankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId())) //银行Code
        paramMap.put("currency", "CNY")
        paramMap.put("mhtorderno", req.getOrderId())
        paramMap.put("notifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("opmhtid", merchantAccount.getMerchantCode())
        paramMap.put("random", req.getOrderId())
        paramMap.put("sign", getSign(merchantAccount.getPrivateKey(), MD5.toAscii(paramMap)))


        log.info("IpayScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/v2/payout/placeOrder", paramMap, 60L, requestHeader)
        log.info("IpayScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "0" != json.getString("rtCode")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("IpayScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderId = resMap.get("mhtorderno")
        String thirdOrderId = resMap.get("pforderno")
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]
        String keyValue = merchant.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mhtorderno", orderId)
        paramMap.put("opmhtid", merchant.getMerchantCode())
        paramMap.put("random", orderId)
        paramMap.put("sign", getSign(merchant.getPrivateKey(), MD5.toAscii(paramMap)))

        log.info("IpayScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())
//        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/v2/payout/getInfo", paramMap, requestHeader)
        String resStr = doGet(merchant.getPayUrl() + "/api/v2/payout/getInfo", paramMap)
        log.info("IpayScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("rtCode") == null || json.getString("rtCode") != "0") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("result")
        if (dataJSON == null) {
            return null
        }
        //订单状态  0：处理中     1：成功      2：失败
        Integer orderState = dataJSON.getInteger("status")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        if (orderState == 1) {
            notify.setStatus(0)
        } else if (orderState == 2) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(thirdOrderId)
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String keyValue = merchantAccount.getPrivateKey() // 商家密钥
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("opmhtid", pay_memberid)
        paramMap.put("random", System.currentTimeMillis().toString())
        paramMap.put("sign", getSign(keyValue, MD5.toAscii(paramMap)))

        log.info("IpayScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
//        String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/api/v2/payout/getBalance", paramMap,  requestHeader)
        String resStr = doGet(merchantAccount.getPayUrl() + "/api/v2/payout/getBalance", paramMap)
        log.info("IpayScript_Query_Balance_resStr: {}", resStr)

        BigDecimal balance = BigDecimal.ZERO
        if (StringUtils.isEmpty(resStr)) {
            return balance
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("rtCode") != "0") {
            return balance
        }
        JSONArray jSONArray = json.getJSONArray("result");
        if (jSONArray == null) {
            return balance
        }
        jSONArray.forEach({ obj ->
            JSONObject dataJSON = (JSONObject) obj;
            if (dataJSON.getString("currency").equals("CNY")) {
                balance =  dataJSON.getBigDecimal("balanceavailable").divide(BigDecimal.valueOf(100))
            }
        })
        return balance;
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

    private static String doGet(String url) {
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