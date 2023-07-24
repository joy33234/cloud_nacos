package com.seektop.fund.payment.huanyupay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.redis.RedisService
import com.seektop.common.utils.BigDecimalUtils
import com.seektop.common.utils.DateUtils
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class HuanYuScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(HuanYuScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private RedisService redisService


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        JSONObject jsObj = new JSONObject();
        jsObj.put("merchNo", merchantAccount.getMerchantCode());
        jsObj.put("orderNo", req.getOrderId());
        jsObj.put("outChannel", "acp");
        jsObj.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        jsObj.put("bankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        jsObj.put("bankNo", req.getCardNo());
        jsObj.put("acctName", req.getName());
        jsObj.put("certNo", "539232199901012343");
        jsObj.put("mobile", "13213221322");
        jsObj.put("userId", req.getUserId().toString());
        jsObj.put("title", "withdraw");
        jsObj.put("product", "withdraw");
        jsObj.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString());
        jsObj.put("currency", "CNY");
        jsObj.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        jsObj.put("reqTime", new Date());

        byte[] context = JSON.toJSONString(jsObj).getBytes("UTF-8");
        String sign = Md5UtilWithdraw.sign(new String(context, "UTF-8"), merchantAccount.getPrivateKey(), "UTF-8");
        JSONObject jo = new JSONObject();
        jo.put("sign", sign);
        jo.put("context", context);
        jo.put("encryptType", "MD5");

        log.info("HuanYuScript_Transfer_params: {}", JSON.toJSONString(jsObj))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
//        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/pay/order/acp", jo.toJSONString(), requestHeader)
        String resStr = getPostResult(merchantAccount.getPayUrl() + "/pay/order/acp", jo.toJSONString())
        log.info("HuanYuScript_Transfer_resStr: {}", resStr)

        JSONObject json = JSONObject.parseObject(resStr);

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(jsObj))
        result.setResData(resStr)
        if (json == null || json.getString("code") != "0") {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }

        JSONObject dataJSON = checkSign(resStr, merchantAccount.getPrivateKey());
        log.info("HuanYuScript_Transfer_resStr_decrpt:{}", dataJSON.toJSONString())
        if (dataJSON == null || "0" != dataJSON.getString("orderState")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("HuanYuScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("orderNo")
        if (org.apache.commons.lang.StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        JSONObject jsObj = new JSONObject();
        jsObj.put("merchNo", merchant.getMerchantCode());
        jsObj.put("orderNo", orderId);
        byte[] context = JSON.toJSONString(jsObj).getBytes("UTF-8");
        String sign = Md5UtilWithdraw.sign(new String(context, "UTF-8"), merchant.getPrivateKey(), "UTF-8");
        JSONObject jo = new JSONObject();
        jo.put("sign", sign);
        jo.put("context", context);
        jo.put("encryptType", "MD5");

        log.info("HuanYuScript_TransferQuery_reqMap:{}", JSON.toJSONString(jsObj))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
//        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/pay/order/acp/query", jo.toJSONString(), requestHeader)
        String resStr = getPostResult(merchant.getPayUrl() + "/pay/order/acp/query", jo.toJSONString())
        log.info("HuanYuScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSONObject.parseObject(resStr);
        if (json == null || json.getString("code") != "0") {
            return null
        }

        JSONObject dataJSON = checkSign(resStr, merchant.getPrivateKey());
        log.info("HuanYuScript_TransferQuery_resStr_decrpt:{}", dataJSON.toJSONString())
        WithdrawNotify notify = new WithdrawNotify()
        //待支付-下单成功:0   支付成功:1   支付失败:2    处理中:3      关闭:4
        if (dataJSON != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            if (dataJSON.getString("orderState") == "1") {
                notify.setStatus(0)
            } else if (dataJSON.getString("orderState") == "2" || dataJSON.getString("orderState") == "4") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        this.redisService = BaseScript.getResource(args[2], ResourceEnum.RedisService) as RedisService

        BigDecimal balance = BigDecimal.ZERO
        String start = getCycleStart(30, new Date())
        String key = String.format("WITHDRAW_MERCHANT_BANLANCE:%s:%s", merchantAccount.getMerchantId(), start);
        String lockKey = key + "_lock";

        Boolean updated = redisService.get(lockKey) == null ? false:true;
        if (updated) {
            balance = redisService.get(key, BigDecimal.class);
        } else {
            balance = getBalance(merchantAccount);
            if (BigDecimalUtils.moreThanZero(balance)) {
                redisService.set(lockKey,true,60)
                redisService.set(key, balance, 90)
            }
        }
        return balance == null ? BigDecimal.ZERO : balance;
    }

    private BigDecimal getBalance(GlWithdrawMerchantAccount merchantAccount) {
        JSONObject jsObj = new JSONObject();
        jsObj.put("merchNo", merchantAccount.getMerchantCode());

        byte[] context = JSON.toJSONString(jsObj).getBytes("UTF-8");
        String sign = Md5UtilWithdraw.sign(new String(context, "UTF-8"), merchantAccount.getPrivateKey(), "UTF-8");
        JSONObject jo = new JSONObject();
        jo.put("sign", sign);
        jo.put("context", context);
        jo.put("encryptType", "MD5");

        log.info("HuanYuScript_QueryBalance_reqMap: {}", JSON.toJSONString(jsObj))
        log.info("HuanYuScript_QueryBalance_reqMap_jo: {}", jo.toJSONString())
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/pay/api/balance/query", jo.toJSONString(), requestHeader)
        log.info("HuanYuScript_QueryBalance_resStr: {}", resStr);

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "0") {
            return BigDecimal.ZERO
        }

        JSONObject dataJSON = checkSign(resStr, merchantAccount.getPrivateKey());
        log.info("HuanYuScript_QueryBalance_resStr_decrpt:{}", dataJSON.toJSONString())
        BigDecimal balance = dataJSON.getBigDecimal("availBal")
        return balance == null ? BigDecimal.ZERO : balance
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



    /**
     * 返回验签
     *
     * @param result
     * @return
     * @throws Exception
     */
    public static JSONObject checkSign(String result, String publicKey) throws Exception {
        JSONObject jo = JSONObject.parseObject(result);
        if ("0".equals(jo.getString("code"))) {
            String sign = jo.getString("sign");
            byte[] context = jo.getBytes("context");
            if (Md5UtilWithdraw.verify(new String(context, "UTF-8"), sign, publicKey, "UTF-8")) {
                jo = JSONObject.parseObject(new String(context, "UTF-8"));
            } else {
                log.info("HuanYuScript_checkSign：验签失败!");
            }
        }
        return jo;
    }



    private String getPostResult(String url, String jsonStr) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(url)
            httpPost.addHeader("Content-Type", "application/json")
            httpPost.setEntity(new StringEntity(jsonStr, "UTF-8"))
            CloseableHttpResponse response = httpclient.execute(httpPost)
            return EntityUtils.toString(response.getEntity(), "UTF-8")
        } catch (IOException e) {
            e.printStackTrace()
        }
        return null
    }


    public static String getCycleStart(Integer cycleTime, Date now) {
        Date startDate = null;
        try {
            Date dailyStartDate = DateUtils.getMinTime(now);
            Integer second = DateUtils.diffSecond(dailyStartDate, now) % cycleTime;
            if (second.intValue() > 0) {
                startDate = org.apache.commons.lang3.time.DateUtils.addSeconds(now, -second);
            } else {
                startDate = now;
            }
        } catch (Exception e) {
            log.error("获取充值周期开始时间错误", e);
        }
        return DateUtils.format(startDate,"yyyyMMddHHmm");
    }
}


class Md5UtilWithdraw {

    public static String MD5(String content) {
        if (content != null && content.length()>0) {
            try {
                return HexUtilWithdraw.byte2hex(MessageDigest.getInstance("md5").digest(content.getBytes()));
            } catch (NoSuchAlgorithmException e) {
                System.out.println("MD5加密错误！" + e.getMessage());
            }
        } else {
            System.out.println("MD5加密内容为空！");
        }
        return null;
    }

    public static String SHA(String content) {
        if (content != null && content.length()>0) {
            try {
                return HexUtilWithdraw.byte2hex(MessageDigest.getInstance("SHA").digest(content.getBytes()));
            } catch (NoSuchAlgorithmException e) {
                System.out.println("SHA加密错误！" + e.getMessage());
                throw new RuntimeException("SHA加密错误！" + e.getMessage());
            }
        } else {
            System.out.println("SHA加密内容为空！");
        }
        return null;
    }

    public static String MD5Update(String content) {
        if (content != null && content.length()>0) {
            MessageDigest messageDigest = null;
            try {
                messageDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                System.out.println("MD5加密错误！" + e.getMessage());
                throw new RuntimeException("MD5加密错误！" + e.getMessage());
            }
            messageDigest.update(content.getBytes());
            return HexUtilWithdraw.byte2hex(messageDigest.digest());
        } else {
            System.out.println("MD5加密内容为空！");
        }
        return null;

    }

    public static String SHAUpdate(String content) {
        if (content != null && content.length()>0) {
            MessageDigest messageDigest = null;
            try {
                messageDigest = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
                System.out.println("SHA加密错误！" + e.getMessage());
                throw new RuntimeException("SHA加密错误！" + e.getMessage());
            }
            messageDigest.update(content.getBytes());
            return HexUtilWithdraw.byte2hex(messageDigest.digest());
        } else {
            System.out.println("SHA加密内容为空！");
        }
        return null;

    }

    public static boolean verifySign(String text,String masterKey,String signature) {
        boolean isVerified = verify(text, signature, masterKey, "UTF-8");
        if (!isVerified) {
            return false;
        }
        return true;
    }


    public static boolean verify(String text, String sign, String key, String inputCharset) {
        text = text + key;
        String mysign = DigestUtils.md5Hex(getContentBytes(text, inputCharset));
        return mysign.equals(sign);
    }

    public static String sign(String text, String key, String inputCharset) {
        text = text + key;
        String mysign = DigestUtils.md5Hex(getContentBytes(text, inputCharset));
        return mysign;
    }


    public static byte[] getContentBytes(String content, String charset) {
        if (charset == null || "".equals(charset)) {
            return content.getBytes();
        }
        try {
            return content.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("MD5签名过程中出现错误,指定的编码集不对,您目前指定的编码集是:" + charset);
        }
    }
}

class HexUtilWithdraw {

    /**
     * 二进制byte数组转十六进制byte数组
     * byte array to hex
     *
     * @param b byte array
     * @return hex string
     */
    public static String byte2hex(byte[] b) {
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (int i = 0; i < b.length; i++) {
            stmp = Integer.toHexString(b[i] & 0xFF);
            if (stmp.length() == 1) {
                hs.append("0").append(stmp);
            } else {
                hs.append(stmp);
            }
        }
        return hs.toString();
    }

    /**
     * 十六进制byte数组转二进制byte数组
     * hex to byte array
     *
     * @param hex hex string
     * @return byte array
     */
    public static byte[] hex2byte(String hex)
            throws IllegalArgumentException{
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException ("invalid hex string");
        }
        char[] arr = hex.toCharArray();
        byte[] b = new byte[hex.length() / 2];
        int  l = hex.length();
        int j = -1;
        for (int i = 0; i < l; i++) {
            String swap = "" + arr[i++] + arr[i];
            j++;
            int byteint = Integer.parseInt(swap, 16) & 0xFF;
            b[j] = new Integer(byteint).byteValue();

            System.out.println(i + "-" + j);
        }
        return b;
    }


}