package com.seektop.fund.payment.sevenelevenpay

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.IvParameterSpec
import java.math.RoundingMode

/**
 * 711支付
 * @auth joy
 */
public class SevenElevenScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(SevenElevenScript_recharge.class)

    private OkHttpUtil okHttpUtil


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        prepareToScan(merchant, account, req, result)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result)  {

        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put("appid", account.getMerchantCode());// 应用key
        map.put("order_trano_in", req.getOrderId());// 商户单号
        map.put("order_goods", "recharge");// 商品名称
        map.put("order_amount", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.DOWN));// 订单金额，单位分 (不能小于100)
        map.put("order_extend", "");// 扩展参数，最大长度64位
        map.put("order_ip", req.getIp());// 客户端真实ip
        map.put("order_return_url", account.getNotifyUrl() + merchant.getId());// 成功后同步地址
        map.put("order_notify_url", account.getNotifyUrl() + merchant.getId());// 异步通知地址

        String jsonContent = getContent(map, account)

        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername() ,req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
        log.info("SevenElevenScrit_recharge_prepare_params = {}", JSON.toJSONString(map))
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/Doc/PayOrder", jsonContent, requestHeader)
        log.info("SevenElevenScrit_recharge_prepare_resp = {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络连接超时")
            return
        }
        JSONObject responseJSON = JSONObject.parseObject(resStr)
        if (responseJSON == null || responseJSON.getString("code") != "1" ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(responseJSON == null ? "创建订单失败" : responseJSON.getString("msg"))
            return
        }
        JSONObject dataJSON = responseJSON.getJSONObject("data")
        if (dataJSON == null || StringUtils.isEmpty(dataJSON.getString("order_pay_url"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(responseJSON == null ? "创建订单失败" : responseJSON.getString("msg"))
            return
        }
        result.setRedirectUrl(dataJSON.getString("order_pay_url"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("SevenElevenScrit_notify = {}", JSONObject.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("order_trano_in")
        String thirdOrderId = json.get("order_number")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.payQuery(okHttpUtil, account, orderId, thirdOrderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String thirdOrderId = args[3] as String
        
        // 拼凑测试数据
        TreeMap<String, String> map = new TreeMap<String, String>();
        map.put("appid", account.getMerchantCode());// 商户单号
        map.put("order_trano_in", orderId);// 商户订单号
        map.put("order_number", thirdOrderId);// 平台订单号

        // 拼接请求用json字符串
        String jsonContent = getContent(map , account)

        log.info("SevenElevenScrit_query_params:{}", jsonContent)
        GlRequestHeader requestHeader =
                getRequestHeard("", "" ,orderId , GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resp = okHttpUtil.postJSON(account.getPayUrl() + "/Doc/PayQuery", jsonContent, requestHeader)
        log.info("SevenElevenScrit_query_resp:{}", resp)

        JSONObject json = JSONObject.parseObject(resp)
        if (json == null ||  "1" != json.getString("code")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        //支付状态,0.待支付  1.支付成功   2.作废
        if (dataJSON != null && "1" == dataJSON.getString("order_state") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("order_amount_real").divide(BigDecimal.valueOf(100).setScale(2,RoundingMode.DOWN)))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("ok")
            return pay
        }
        return null
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

    private String getContent(TreeMap<String, String> map, GlPaymentMerchantaccount account) {

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