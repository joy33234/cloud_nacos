package com.seektop.fund.payment.weilipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.PaymentMerchantEnum
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

public class WeiliScript_withdraw {

    private Map<Integer, String> bankCodeMap = new HashMap<Integer, String>() {
        {
            put(1, "03080000")
            put(2, "01020000")
            put(3, "01050000")
            put(4, "01030000")
            put(5, "01040000")
            put(6, "03010000")
            put(7, "03060000")
            put(8, "03030000")
            put(9, "03100000")
            put(10, "03050000")
            put(11, "03070000")
            put(12, "03090000")
            put(13, "03020000")
            put(14, "04030000")
            put(15, "03040000")
            //银行编码
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WeiliScript_withdraw.class)

    private OkHttpUtil okHttpUtil



    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        Map<String, String> param = new LinkedHashMap<>()
        String keyValue = merchantAccount.getPrivateKey()// 私钥
        param.put("txnType", "52")
        param.put("txnSubType", "10")
        param.put("secpVer", "icp3-1.1")
        param.put("secpMode", "perm")
        param.put("macKeyId", merchantAccount.getMerchantCode())//密钥编号，由平台提供，现与商户号相同

        Date now = new Date()
        param.put("orderDate", DateUtils.getStrCurDate(now, DateUtils.YYYYMMDD))
        param.put("orderTime", DateUtils.getStrCurDate(now, DateUtils.HHMMSS))

        param.put("merId", merchantAccount.getMerchantCode())
        param.put("orderId", req.getOrderId())
        //金额单位为分
        param.put("txnAmt", (req.getAmount().subtract(req.getFee()) * BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        param.put("currencyCode", "156")
        param.put("accName", req.getName())
        param.put("accNum", req.getCardNo())
        param.put("bankNum", bankCodeMap.get(req.getBankId()))
        param.put("bankName", req.getBankName())
        param.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        param.put("timeStamp", DateUtils.getStrCurDate(now, DateUtils.YYYYMMDDHHMMSS))

        String signStrTemp = MD5.toAscii(param) + "&k=" + keyValue
        param.put("mac", MD5.md5(signStrTemp))

        log.info("WeiliScript_Transfer_OrderId:{},data:{}", req.getOrderId(), JSON.toJSONString(param))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(PaymentMerchantEnum.WEILI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WEILI_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/powerpay-gateway-onl/txn", param, requestHeader)
        log.info("WeiliScript_Transfer_OrderId:{},response: {}", req.getOrderId(), retBack)
        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(param))
        result.setResData(retBack)

        if (StringUtils.isEmpty(retBack)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(retBack)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        String respCode = json.getString("respCode") //响应码
        String txnStatus = json.getString("txnStatus")//交易状态
        if (StringUtils.isEmpty(respCode) || StringUtils.isEmpty(txnStatus)) {
            result.setValid(false)
            result.setMessage(json.getString("respMsg"))
            return result
        }
        if (respCode != ("0000") && respCode != ("1101")) {
            result.setValid(false)
            result.setMessage(json.getString("respMsg"))
            return result
        }
        if (txnStatus != ("01") && txnStatus != ("10")) {
            result.setValid(false)
            result.setMessage(json.getString("respMsg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("respMsg"))
        return result
    }


    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("WeiliScript_doTransferNotify_resMap:{}", resMap)
        // 修改为验签模式 20191015
        if (StringUtils.isNotEmpty(resMap.get("respCode"))) {
            // 接口成功返回  验证 业务状态是否成功
            String mac = resMap.remove("mac")
            resMap.remove("reqBody")
            String signStrTemp = toAscii(resMap) + "&k=" + merchant.getPrivateKey()
            if (mac == (MD5.md5(signStrTemp))) {
                log.info("Weili_Script_doTransferNotify_sign_success:{}", resMap.get("txnStatus"))
                WithdrawNotify notify = new WithdrawNotify()
                notify.setAmount(new BigDecimal(resMap.get("txnAmt")).divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
                notify.setMerchantCode(merchant.getMerchantCode())
                notify.setMerchantId(merchant.getMerchantId())
                notify.setMerchantName(merchant.getChannelName())
                notify.setOrderId(resMap.get("orderId"))
                notify.setThirdOrderId(resMap.get("txnId"))
                if ("10" == (resMap.get("txnStatus"))) {//成功
                    notify.setStatus(0)
                } else if ("20" == (resMap.get("txnStatus"))) {
                    notify.setStatus(1)
                } else {
                    notify.setStatus(2)
                }
                notify.setSuccessTime(Date.from(LocalDateTime.parse(resMap.get("timeStamp"),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atZone(ZoneId.systemDefault()).toInstant()))
                return notify
            }
        }
        return null
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
                sign.append(k + "=" + v + "&")
            }
        }
        return sign.deleteCharAt(sign.length() - 1).toString()
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        String orderDate = orderId.substring(2, 10)
        Map<String, String> param = new LinkedHashMap<>()
        String keyValue = merchant.getPrivateKey()// 私钥
        param.put("txnType", "00")
        param.put("txnSubType", "50")
        param.put("secpVer", "icp3-1.1")
        param.put("secpMode", "perm")
        param.put("macKeyId", merchant.getMerchantCode())//密钥编号，由平台提供，现与商户号相同
        param.put("merId", merchant.getMerchantCode())
        Date now = new Date()
        param.put("orderId", orderId)
        param.put("orderDate", orderDate)
        param.put("timeStamp", DateUtils.getStrCurDate(now, DateUtils.YYYYMMDDHHMMSS))

        String signStrTemp = MD5.toAscii(param) + "&k=" + keyValue
        param.put("mac", MD5.md5(signStrTemp))
        log.info("WeiliScript_Transfer_query_params:{}", JSON.toJSONString(param))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW_QUERY.getCode())
                .channelId(PaymentMerchantEnum.WEILI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WEILI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build()
        String retBack = okHttpUtil.post(merchant.getPayUrl() + "/powerpay-gateway-onl/txn", param, requestHeader)
        log.info("WeiliScript_Transfer_query_result:{}", retBack)

        JSONObject retJson = JSONObject.parseObject(retBack)
        String respCode = retJson.getString("respCode")
        String txnStatus = retJson.getString("txnStatus")
        if (StringUtils.isNotEmpty(respCode) && "0000" == (respCode)) {
            WithdrawNotify notify = new WithdrawNotify()
            notify.setAmount(retJson.getBigDecimal("txnAmt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(retJson.getString("txnId"))

            if (StringUtils.isNotEmpty(txnStatus) && "10" == (txnStatus)) {//成功
                notify.setStatus(0)
            } else if (StringUtils.isNotEmpty(txnStatus) && "20" == (txnStatus)) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            notify.setSuccessTime(retJson.getDate("timeStamp"))
            return notify
        }
        return null
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        String keyValue = merchantAccount.getPrivateKey()// 接口密钥
        Map<String, String> param = new HashMap<>()
        param.put("txnType", "00")
        param.put("txnSubType", "90")
        param.put("secpVer", "icp3-1.1")
        param.put("secpMode", "perm")
        param.put("macKeyId", merchantAccount.getMerchantCode())//密钥编号，由平台提供，现与商户号相同
        param.put("merId", merchantAccount.getMerchantCode())
        Date now = new Date()
        param.put("accCat", "00")
        param.put("timeStamp", DateUtils.getStrCurDate(now, DateUtils.YYYYMMDDHHMMSS))

        String signStrTemp = MD5.toAscii(param) + "&k=" + keyValue

        param.put("mac", MD5.md5(signStrTemp))

        log.info("WeiliScript_QueryBalance_params:{}", JSON.toJSONString(param))
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
                .channelId(PaymentMerchantEnum.WEILI_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.WEILI_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId("")
                .build()
        String retBack = okHttpUtil.post(merchantAccount.getPayUrl() + "/powerpay-gateway-onl/txn", param, requestHeader)
        log.info("WeiliScript_QueryBalance_resStr:{}", retBack)

        JSONObject json = JSON.parseObject(retBack)
        if (null == json) {
            return BigDecimal.ZERO
        }

        String respCode = json.getString("respCode")
        if (StringUtils.isNotEmpty(respCode) && respCode == ("0000")) {
            return json.getBigDecimal("balance").divide(BigDecimal.valueOf(100).setScale(2, RoundingMode.DOWN))
        }
        return BigDecimal.ZERO
    }

    public boolean isJsonString(String str) {
        boolean result = false
        try {
            JSONObject retJson = JSONObject.parseObject(str)
            result = true
        } catch (Exception e) {
            result = false
        }
        return result
    }
}
