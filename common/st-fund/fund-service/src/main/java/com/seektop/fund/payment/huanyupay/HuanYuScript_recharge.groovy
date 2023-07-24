package com.seektop.fund.payment.huanyupay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
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

class HuanYuScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HuanYuScript_recharge.class)

    private OkHttpUtil okHttpUtil


    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "wyyhk"
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        JSONObject jsObj = new JSONObject();
        jsObj.put("merchNo", payment.getMerchantCode());
        jsObj.put("orderNo", req.getOrderId());
        jsObj.put("outChannel", payType);
        jsObj.put("userId", req.getUserId().toString());
        jsObj.put("title", "recharge");
        jsObj.put("product", "recharge");
        jsObj.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString());
        jsObj.put("currency", "CNY");
        jsObj.put("returnUrl", payment.getNotifyUrl() + merchant.getId());
        jsObj.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
        jsObj.put("reqTime", req.getCreateDate());
        jsObj.put("supportType", "0");
        jsObj.put("realname", req.getFromCardUserName());


        byte[] context = JSON.toJSONString(jsObj).getBytes("UTF-8");
        String sign = Md5Util.sign(new String(context, "UTF-8"), payment.getPublicKey(), "UTF-8");
        JSONObject jo = new JSONObject();
        jo.put("sign", sign);
        jo.put("context", context);
        jo.put("encryptType", "MD5");

        log.info("HuanYuScript_Prepare_Params:{}", JSON.toJSONString(jsObj))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
//        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/order", jo.toJSONString(),  requestHeader)
        String restr = getPostResult(payment.getPayUrl() + "/pay/order", jo.toJSONString())
        log.info("HuanYuScript_Prepare_resStr:{}", restr)


        JSONObject json = JSONObject.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("code") != "0") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = checkSign(restr, payment.getPublicKey());
        log.info("HuanYuScript_Prepare_resStr_decrpt:{}", dataJSON.toJSONString())
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("code_url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("code_url"))

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>

        log.info("HuanYuScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("orderNo")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        JSONObject jsObj = new JSONObject();
        jsObj.put("merchNo", account.getMerchantCode());
        jsObj.put("orderNo", orderId);

        byte[] context = JSON.toJSONString(jsObj).getBytes("UTF-8");
        String sign = Md5Util.sign(new String(context, "UTF-8"), account.getPublicKey(), "UTF-8");
        JSONObject jo = new JSONObject();
        jo.put("sign", sign);
        jo.put("context", context);
        jo.put("encryptType", "MD5");

        log.info("HuanYuScript_Query_reqMap:{}", JSON.toJSONString(jsObj))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
//        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/pay/order/query", jo.toJSONString(),  requestHeader)
        String resStr = getPostResult(account.getPayUrl() + "/pay/order/query", jo.toJSONString())
        log.info("HuanYuScript_Query_resStr:{}", resStr)

        JSONObject json = checkSign(resStr, account.getPublicKey());
        log.info("HuanYuScript_Query_resStr_decrpt:{}", json.toJSONString())
        //支付完成：待支付-下单成功:0   支付成功:1   支付失败:2    处理中:3      关闭:4
        if (json != null && "1" == json.getString("orderState")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("realAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("ok")
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
        return true
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
            if (Md5Util.verify(new String(context, "UTF-8"), sign, publicKey, "UTF-8")) {
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
}

class Md5Util {

   public static String MD5(String content) {
       if (content != null && content.length()>0) {
           try {
               return HexUtil.byte2hex(MessageDigest.getInstance("md5").digest(content.getBytes()));
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
               return HexUtil.byte2hex(MessageDigest.getInstance("SHA").digest(content.getBytes()));
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
           return HexUtil.byte2hex(messageDigest.digest());
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
           return HexUtil.byte2hex(messageDigest.digest());
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

class HexUtil {

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