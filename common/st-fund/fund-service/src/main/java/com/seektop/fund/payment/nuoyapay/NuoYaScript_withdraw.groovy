package com.seektop.fund.payment.nuoyapay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class NuoYaScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(NuoYaScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness



    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", merchantAccount.getMerchantCode())
        paramMap.put("df_mch_order_no", req.getOrderId())
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        paramMap.put("acc_name", req.getName())
        paramMap.put("bank_account", req.getCardNo())
        paramMap.put("bank_name", req.getBankName())
        paramMap.put("bank_branch", "支行")
        paramMap.put("bank_province", "上海市")
        paramMap.put("bank_city", "上海市")
        paramMap.put("notify_url", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String toSign = MD5.toAscii(paramMap) + "&signkey=" + merchantAccount.getPrivateKey()
        toSign = SHA256Util1.getSha256(toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("NuoYaScript_Transfer_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/payment/dfpay/api.html", JSONObject.toJSONString(paramMap),  requestHeader)
        log.info("NuoYaScript_Transfer_resStr: {}", resStr)

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
        if (json == null || "success" != json.getString("status") || StringUtils.isEmpty(json.getString("transaction_id"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("msg"))
        result.setThirdOrderId(json.getString("transaction_id"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("NuoYaScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("df_mch_order_no")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid)
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", merchant.getMerchantCode())
        paramMap.put("df_mch_order_no", orderId)

        String toSign = MD5.toAscii(paramMap) + "&signkey=" + merchant.getPrivateKey()
        toSign = SHA256Util1.getSha256(toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("NuoYaScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(), merchant.getChannelName())

        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/payment/dfpay/query.html", JSONObject.toJSONString(paramMap),  requestHeader)
        log.info("NuoYaScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return null
        }
        Integer refCode = json.getInteger("refCode")
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(json.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        // 1:成功  2:失败  3:处理中 4:待处理  5:审核驳回  6:待审核  7：交易不存在  8：未知状态
        if (refCode == 1) {
            notify.setStatus(0)
        } else if (refCode == 2 || refCode == 5) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        notify.setThirdOrderId(json.getString("transaction_id"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String pay_memberid = merchantAccount.getMerchantCode()

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mch_id", pay_memberid)
        paramMap.put("random_string", System.currentTimeMillis().toString())

        String toSign = MD5.toAscii(paramMap) + "&signkey=" + merchantAccount.getPrivateKey()
        log.info("toSign:{}",toSign)
        toSign = SHA256Util1.getSha256(toSign)
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())
        log.info("NuoYaScript_Query_Balance_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/pay/trade/balance.html", JSON.toJSONString(paramMap),  requestHeader)
        log.info("NuoYaScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") != "success") {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON)) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance
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


}


class SHA256Util1 {

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
        return CodingUtil2.bytesToHexString(SHA256.digest());
    }

}


class CodingUtil2 {

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
        HexAppender2 helper = new HexAppender2(src.length * 2);
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
        HexAppender2 helper = new HexAppender2(src.length * 2);
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

    private static class HexAppender2 {

        private int    offerSet = 0;
        private char[] charData;

        public HexAppender2(int size) {
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
        HexAppender2 helper = new HexAppender2((src.length - startWith) * 2);
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
