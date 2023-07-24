package com.seektop.fund.payment.yudianPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
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

/**
 * @desc 雨点支付
 * @date 2021-07-20
 * @auth joy
 */
public class YuDianScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(YuDianScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("spId", account.getMerchantCode())
        params.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        params.put("acctId", req.getCardNo())
        params.put("acctName", req.getName())
        params.put("bankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        params.put("userDfId", req.getOrderId())
        params.put("callbackUrl", account.getNotifyUrl() + account.getMerchantId())

        String toSign = this.toAscii(params)  + account.getPrivateKey()
        params.put("sign", SHA256Util.getSha256(toSign.getBytes()))

        log.info("YuDianScript_doTransfer_params：{}",  JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/df/spSingleDf", params, requestHeader)
        log.info("YuDianScript_doTransfer_resp:{}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) ||  "10000" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON) || StringUtils.isEmpty(dataJSON.getString("dfId"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        result.setValid(true)
        result.setMessage("ok")
        result.setThirdOrderId(dataJSON.getString("dfId"))
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("YuDianScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("userDfId");
        } else {
            orderId = json.getString("userDfId");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("spId", merchant.getMerchantCode())
        params.put("userDfId", orderId)

        String toSign = this.toAscii(params)  + merchant.getPrivateKey()
        params.put("sign", SHA256Util.getSha256(toSign.getBytes()))


        log.info("YuDianScript_doTransferQuery_params:{}",  JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/df/dfOrderQuery",params , requestHeader)
        log.info("YuDianScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "10000") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON)) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("transactionId"))
        //代付结果:  wait("待处理"), processing("处理中"), success("代付成功"),fail("代付失败");
        if (dataJSON.getString("dfState") == ("success")) {
            notify.setStatus(0)
        } else if (dataJSON.getString("dfState") == ("fail")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new LinkedHashMap<>()
        params.put("spId", merchantAccount.getMerchantCode())

        String toSign = this.toAscii(params)  + merchantAccount.getPrivateKey()
        params.put("sign", SHA256Util.getSha256(toSign.getBytes()))

        log.info("YuDianScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/df/spAccount", params,  requestHeader)
        log.info("YuDianScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "10000" != (json.getString("code"))) {
            return BigDecimal.ZERO
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (ObjectUtils.isEmpty(dataJSON)) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("totalAmount").subtract(dataJSON.getBigDecimal("freezeAmount"))
        return balance == null ? BigDecimal.ZERO : balance.divide(BigDecimal.valueOf(100))
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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
     * ASCII排序
     *
     * @param parameters
     * @return
     */
    public static String toAscii(Map<String, String> parameters) {
        List<Map.Entry<String, String>> infoIds = new ArrayList<>(parameters.entrySet())
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        Collections.sort(infoIds, Comparator.comparing({ o -> (o as Map.Entry<String, String>).getKey() }))
        StringBuffer sign = new StringBuffer()
        for (Map.Entry<String, String> item : infoIds) {
            String k = item.getKey()
            if (!org.springframework.util.StringUtils.isEmpty(item.getKey())) {
                Object v = item.getValue()
                sign.append(v + "|")
            }
        }
        return sign.toString();
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



