package com.seektop.fund.payment.nuoyapay

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
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


/**
 * 诺亚支付
 * @auth joy
 * @date 2021-05-16
 *
 */
class NuoYaScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(NuoYaScript_recharge.class)

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

        String paymentType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            paymentType = "101"
        }
        if (StringUtils.isNotEmpty(paymentType)) {
            prepareScan(merchant, payment, req, result, paymentType)
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("该充值方式暂不支持")
            return
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String pay_bankcode) {
        String keyValue = payment.getPrivateKey() // 商家密钥
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", payment.getMerchantCode())
        paramMap.put("mch_order_no", req.getOrderId())
        paramMap.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("method", pay_bankcode)
        paramMap.put("format", "PAGE")
        paramMap.put("notify_url", payment.getResultUrl() + merchant.getId())
        paramMap.put("callback_url", payment.getResultUrl() + merchant.getId())
        paramMap.put("random_string", MD5.md5(req.getOrderId()))

        String toSign = MD5.toAscii(paramMap) + "&signkey=" + keyValue
        toSign = SHA256Util.getSha256(toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())
        paramMap.put("buyer_name", req.getFromCardUserName())//付款人

        log.info("NuoYaScript_Prepare_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/pay/index/api.html", JSONObject.toJSONString(paramMap),  requestHeader)
        log.info("NuoYaScript_Prepare_Resp: {}", restr)
        if (StringUtils.isEmpty(restr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json  = JSONObject.parseObject(restr)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("三方返回数据错误")
            return
        }

        if (json.getString("status") != "success") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        result.setThirdOrderId(dataJSON.getString("trans_order_no"))
        result.setRedirectUrl(dataJSON.getString("pay_url"))
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("NuoYaScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("mch_order_no")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String keyValue = payment.getPrivateKey() // 商家密钥

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", payment.getMerchantCode())
        paramMap.put("mch_order_no", orderId)
        paramMap.put("random_string", System.currentTimeMillis().toString())

        String toSign = MD5.toAscii(paramMap) + "&signkey=" + keyValue
        log.info("toSign:{}",toSign)
        toSign = SHA256Util.getSha256(toSign)
        log.info("toSign:{}",toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        String queryUrl = payment.getPayUrl() + "/pay/trade/query.html"
        log.info("NuoYaScript_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.postJSON(queryUrl, JSON.toJSONString(paramMap), requestHeader)
        log.info("NuoYaScript_Query_resStr: {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null || "success" != json.getString("status")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON)) {
            return null
        }
        //支付完成：0:未支付 1:支付未返回 2:支付已返回
        if (dataJSON.getString("status") == "1" || dataJSON.getString("status") == "2") {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("real_amount").setScale(2, RoundingMode.DOWN))//会员实际支付金额
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(dataJSON.getString("trans_order_no"))
            return pay
        }
        return null
    }


    void cancel(Object[] args) throws GlobalException {

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


}


class SHA256Util {

    private static MessageDigest SHA256;

    static {
        try {
            SHA256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String getSha256(String msg) {
        return getSha256(msg.getBytes());
    }

    public static synchronized String getSha256(byte[] msg) {
        SHA256.update(msg);
        return CodingUtil.bytesToHexString(SHA256.digest());
    }

}


public class CodingUtil {

    private static final int BIT_SIZE   = 0x10;
    private static final int BIZ_ZERO   = 0X00;

    private static char[][]  charArrays = new char[256][];

    static {
        int v;
        char[] ds;
        String temp;
        for (int i = 0; i < charArrays.length; i++) {
            ds = new char[2];
            v = i & 0xFF;
            temp = Integer.toHexString(v);
            if (v < BIT_SIZE) {
                ds[0] = '0';
                ds[1] = temp.charAt(0);
            } else {
                ds[0] = temp.charAt(0);
                ds[1] = temp.charAt(1);
            }
            charArrays[i] = ds;
        }
    }

    public static String bytesToHexString(byte[] src) {
        HexAppender helper = new HexAppender(src.length * 2);
        if (src == null || src.length <= BIZ_ZERO) {
            return null;
        }
        int v;
        char[] temp;
        for (int i = 0; i < src.length; i++) {
            v = src[i] & 0xFF;
            temp = charArrays[v];
            helper.append(temp[0], temp[1]);
        }
        return helper.toString();
    }

    public static String bytesToHexStringSub(byte[] src, int length) {
        HexAppender helper = new HexAppender(src.length * 2);
        if (src == null || src.length <= BIZ_ZERO) {
            return null;
        }
        int v;
        char[] temp;
        for (int i = 0; i < length; i++) {
            v = src[i] & 0xFF;
            temp = charArrays[v];
            helper.append(temp[0], temp[1]);
        }
        return helper.toString();
    }

    /**
     * Convert hex string to byte[]
     *
     * @param hexString the hex string
     * @return byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (null == hexString || "".equals(hexString)) {
            return null;
        }
        int length = hexString.length() / 2;
        byte[] d = new byte[length];
        int pos;
        for (int i = 0; i < length; i++) {
            pos = i * 2;
            d[i] = (byte) (charToByte(hexString.charAt(pos)) << 4 | charToByte(hexString.charAt(pos + 1)));
        }
        return d;
    }

    /**
     * Convert char to byte
     *
     * @param c char
     * @return byte
     */
    private static byte charToByte(char c) {
        return (byte) (c < 58 ? c - 48 : c < 71 ? c - 55 : c - 87);
    }

    private static class HexAppender {

        private int    offerSet = 0;
        private char[] charData;

        public HexAppender(int size) {
            charData = new char[size];
        }

        public void append(char a, char b) {
            charData[offerSet++] = a;
            charData[offerSet++] = b;
        }

        @Override
        public String toString() {
            return new String(charData, 0, offerSet);
        }
    }

    public static String bytesToHexString(byte[] src, int startWith) {
        HexAppender helper = new HexAppender((src.length - startWith) * 2);
        if (src == null || src.length <= BIZ_ZERO) {
            return null;
        }
        int v;
        char[] temp;
        for (int i = startWith; i < src.length; i++) {
            v = src[i] & 0xFF;
            temp = charArrays[v];
            helper.append(temp[0], temp[1]);
        }
        return helper.toString();
    }
}
