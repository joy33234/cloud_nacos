package com.seektop.fund.payment.baomapay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.BigDecimalUtils
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.IvParameterSpec
import java.math.RoundingMode

/**
 * 宝马支付
 * @auth joy
 * @date 2021-04-15
 */
public class BaomaScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(BaomaScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put("appid", account.getMerchantCode());// 应用key
        map.put("order_trano_in", req.getOrderId());// 商户单号
        map.put("order_account_name", req.getName());
        map.put("order_account_no", req.getCardNo());
        map.put("order_account_no", req.getCardNo());
        map.put("order_bank_name", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()));
        map.put("order_bank_code", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
        map.put("order_bank_branch", "上海市");
        map.put("order_bank_firm_no", "123456");
        map.put("order_bank_city", "上海市");
        map.put("order_bank_province", "上海市");
        map.put("order_amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.DOWN));// 订单金额，单位分
        map.put("order_notify_url", account.getNotifyUrl() + account.getMerchantId());

        String jsonContent = getContent(map, account)

        log.info("BaomaScript_transfer_params:{}", JSON.toJSONString(map))
        GlRequestHeader requestHeader =
                getRequestHeard( req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/Doc/DaiFuOrder", jsonContent , requestHeader)
        log.info("BaomaScript_transfer_resp:{}", resStr)
        
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(jsonContent)
        result.setResData(resStr)

        JSONObject responseJSON = JSONObject.parseObject(resStr)
        if (responseJSON == null || responseJSON.getString("code") != "1" ) {
            result.setValid(false)
            result.setMessage(responseJSON == null ? "API异常:请联系出款商户确认订单." : responseJSON.getString("msg"))
            return result
        }

        JSONObject dataJSON = responseJSON.getJSONObject("data")
        if(dataJSON == null || (dataJSON.getString("order_state") != "1" && dataJSON.getString("order_state") != "0")) {
            result.setValid(false)
            result.setMessage(responseJSON == null ? "API异常:请联系出款商户确认订单." : responseJSON.getString("msg"))
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
        log.info("BaomaScript_withdrawNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_trano_in")
        String thirdOrderId = json.get("order_number")
        if (StringUtils.isEmpty(orderId) || StringUtils.isEmpty(thirdOrderId)) {
            return null
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId, thirdOrderId , args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String thirdOrderId = args[3]

        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put("appid", account.getMerchantCode());// 应用key
        map.put("order_number", thirdOrderId);// 平台单号

        String jsonContent = getContent(map, account)

        log.info("BaomaScript_transferQuery_params:{}", JSON.toJSONString(map))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/Doc/DaiFuQuery", jsonContent , requestHeader)
        log.info("BaomaScript_transferQuery_resp:{}", resStr)
        JSONObject responseJSON = JSONObject.parseObject(resStr)

        if (responseJSON == null || StringUtils.isEmpty(responseJSON.getString("code"))) {
            return null
        }

        JSONObject dataJSON = responseJSON.getJSONObject("data")
        if (dataJSON == null ) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("order_amount").divide(BigDecimal.valueOf(100)).setScale(2,RoundingMode.DOWN))
            notify.setMerchantCode(account.getMerchantCode())
            notify.setMerchantId(account.getMerchantId())
            notify.setMerchantName(account.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(thirdOrderId)
            //状态：  0.待处理   1.处理中   2.成功   3.失败
            if (dataJSON.getString("order_state") == "2") {
                notify.setStatus(0)
                notify.setRsp("ok")
            } else if (dataJSON.getString("order_state") == "3") {
                notify.setStatus(1)
                notify.setRsp("ok")
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put("appid", merchantAccount.getMerchantCode());// 应用key

        String jsonContent = getContent(map, merchantAccount)

        log.info("BaomaScript_queryBalance_params:{}", JSON.toJSONString(map))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/Doc/DaiFuBalance", jsonContent, requestHeader)
        log.info("BaomaScript_queryBalance_resp:{}", resStr)
        JSONObject json = JSONObject.parseObject(resStr)
        if ("1" == (json.getString("code"))) {
            JSONObject dataJSON = json.getJSONObject("data");
            if (dataJSON != null && BigDecimalUtils.moreThanZero(dataJSON.getBigDecimal("amount"))) {
                return dataJSON.getBigDecimal("amount").divide(BigDecimal.valueOf(100))
            }
        }
        return BigDecimal.ZERO
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

    private String getContent(TreeMap<String, String> map, GlWithdrawMerchantAccount account) {

        String jsonString = JSON.toJSONString(map)

        // 根据key排序asc的str字符串
        String sortStr = sortSign(map);

        // 时间戳
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 随机字符串
        String nonce = genNonceStr();

        // DES加密key 0-8
        String signDesKey = MD5.md5(timestamp + account.getPublicKey() + nonce ).substring(0, 8).toUpperCase() ;//平台公钥
        log.info(signDesKey)
        // 支付加密类型
        String signtype = "MD5";

        // 公钥加密
        String data = encrypt(jsonString, signDesKey);
        // 私钥签名
        String sign = MD5.md5(timestamp + nonce + sortStr + account.getPrivateKey()).toUpperCase();//应用密钥

        // 拼接请求用json字符串
        return getJsonContent(data, sign, timestamp, nonce, signtype).toString();
    }

    // 最终发送的数据串
    public static JSONObject getJsonContent(String data, String sign, String timestamp, String nonce,
                                            String signtype) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("data", data);
        jsonObject.put("sign", sign);
        jsonObject.put("timestamp", timestamp);
        jsonObject.put("nonce", nonce);
        jsonObject.put("signtype", signtype);
        return jsonObject;
    }



    public static String sortSign(TreeMap<String, String> map) {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (!value.isEmpty()) {
                sb.append(String.format("%s%s", entry.getKey(), value));
            }
        }
        return sb.toString();
    }

    /**
     * 随机字符串
     *
     * @return 获取随字符串
     */
    public static String genNonceStr() {
        Random random = new Random();
        return MD5.md5(String.valueOf(random.nextInt(10000))).toUpperCase();
    }


    /**
     * 加密
     *
     * @param message 加密内容
     * @param key     加密密钥(明文)
     * @return
     */
    public static String encrypt(String message, String key) {
        try {
            return byteArr2HexStr(_encrypt(message,  key ));
        } catch (Exception e) {
        }
        return message;
    }

    /**
     * 加密
     *
     * @param message 加密内容
     * @param key     加密密钥(md5 0-8)
     * @return
     * @throws Exception
     */
    public static byte[] _encrypt(String message, String key)
            throws Exception {
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        DESKeySpec desKeySpec = new DESKeySpec(key.getBytes("UTF-8"));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey secretKey = keyFactory.generateSecret(desKeySpec);
        IvParameterSpec iv = new IvParameterSpec(key.getBytes("UTF-8"));
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        return cipher.doFinal(message.getBytes("UTF-8"));
    }

    public static String byteArr2HexStr(byte[] arrB) throws Exception {
        int iLen = arrB.length;
        // 每个byte用两个字符才能表示，所以字符串的长度是数组长度的两倍
        StringBuffer sb = new StringBuffer(iLen * 2);
        for (int i = 0; i < iLen; i++) {
            int intTmp = arrB[i];
            // 把负数转换为正数
            while (intTmp < 0) {
                intTmp = intTmp + 256;
            }
            // 小于0F的数需要在前面补0
            if (intTmp < 16) {
                sb.append("0");
            }
            sb.append(Integer.toString(intTmp, 16));
        }
        return sb.toString();
    }


    /**
     * 解密
     *
     * @param message 密文内容
     * @param key     解密密钥(明文)
     * @return
     * @throws Exception
     */
    public static String decrypt(String message, String key) {
        try {
            return _decrypt(message, key );
        } catch (Exception e) {
        }
        return message;
    }

    /**
     * @param message 密文内容
     * @param key     解密密钥(md5 0-8)
     * @return
     * @throws Exception
     */
    public static String _decrypt(String message, String key) throws Exception {
        byte[] bytesrc = hexStr2ByteArr(message);
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        DESKeySpec desKeySpec = new DESKeySpec(key.getBytes("UTF-8"));
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey secretKey = keyFactory.generateSecret(desKeySpec);
        IvParameterSpec iv = new IvParameterSpec(key.getBytes("UTF-8"));
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        byte[] retByte = cipher.doFinal(bytesrc);
        return new String(retByte).trim().replace("\n", "");
    }

    public static byte[] hexStr2ByteArr(String strIn) throws Exception {
        byte[] arrB = strIn.getBytes();
        int iLen = arrB.length;
        // 两个字符表示一个字节，所以字节数组长度是字符串长度除以2
        byte[] arrOut = new byte[iLen / 2];
        for (int i = 0; i < iLen; i = i + 2) {
            String strTmp = new String(arrB, i, 2);
            arrOut[i / 2] = (byte) Integer.parseInt(strTmp, 16);
        }
        return arrOut;
    }
}