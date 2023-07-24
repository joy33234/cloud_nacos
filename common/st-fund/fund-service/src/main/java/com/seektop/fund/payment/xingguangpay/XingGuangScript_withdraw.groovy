package com.seektop.fund.payment.xingguangpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.AlgorithmParameters
import java.security.Security

/**
 * @desc 星光支付
 * @date 2021-09-15
 * @auth joy
 */
public class XingGuangScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(XingGuangScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("userId", req.getUserId().toString())
        params.put("orderId", req.getOrderId())
        params.put("orderTime", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("account", req.getCardNo())
        params.put("holder", req.getName())
        params.put("bank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())
        params.put("nonce", MD5.md5(req.getOrderId()).substring(0,9))
        params.put("timestamp", System.currentTimeMillis().toString())

        JSONObject jsonParams = new JSONObject();
        jsonParams.put("id", account.getMerchantCode());
        jsonParams.put("data", encrypt(JSON.toJSONString(params),account.getPrivateKey()))

        log.info("XingGuangScript_doTransfer_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/biz/place_withdrawal_order", JSON.toJSONString(jsonParams), requestHeader)
        resStr = decrypt(resStr, account.getPrivateKey())
        log.info("XingGuangScript_doTransfer_resp:{}", resStr)

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
        if (org.springframework.util.ObjectUtils.isEmpty(json) || "0" != (json.getString("code"))) {
            result.setValid(false)
            result.setMessage(json.getString("msg"))
            return result
        }
        req.setThirdOrderId(json.getString("transId"))
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("XingGuangScript_doTransferNotify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String data = json.getString("data")
        data = decrypt(data, merchant.getPrivateKey())
        JSONObject dataJSON = JSONObject.parseObject(data);
        String orderId = dataJSON.getString("orderId")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", merchant.getMerchantCode())
        params.put("orderId", orderId)
        params.put("nonce", MD5.md5(orderId).substring(0,9))
        params.put("timestamp", System.currentTimeMillis().toString())

        JSONObject jsonParams = new JSONObject();
        jsonParams.put("id", merchant.getMerchantCode());
        jsonParams.put("data", encrypt(JSON.toJSONString(params),merchant.getPrivateKey()))


        log.info("XingGuangScript_doTransferQuery_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/api/biz/query_withdrawal_order", JSONObject.toJSONString(jsonParams), requestHeader)
        resStr = decrypt(resStr, merchant.getPrivateKey())
        log.info("XingGuangScript_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (org.apache.commons.lang3.ObjectUtils.isEmpty(json) || "0" != (json.getString("code"))) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(json.getString("transId"))
        // 支付状态:status (0, '成功') (1, '创建'), (2, '处理中'), (3, '交易成功'), (4, '交易失败'), (5, '超时过期'), (11, '取消'),
        if (json.getString("status") == ("0")) {
            notify.setStatus(0)
        } else if (json.getString("status") == ("4") || json.getString("status") == ("11") ) {
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
        params.put("merchantId", merchantAccount.getMerchantCode())
        String timestamp = System.currentTimeMillis().toString();
        params.put("timestamp", timestamp)
        params.put("nonce", MD5.md5(timestamp).substring(0,9))

        JSONObject jsonParams = new JSONObject();
        jsonParams.put("id", merchantAccount.getMerchantCode());
        jsonParams.put("data", encrypt(JSON.toJSONString(params),merchantAccount.getPrivateKey()))

        log.info("XingGuangScript_Query_Balance_Params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/api/biz/query_merchant_balance", JSONObject.toJSONString(jsonParams),  requestHeader)
        log.info("XingGuangScript_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        resStr = decrypt(resStr, merchantAccount.getPrivateKey())
        JSONObject json = JSON.parseObject(resStr)
        if (ObjectUtils.isEmpty(json) || "0" != (json.getString("code"))) {
            return null
        }

        BigDecimal balance = json.getBigDecimal("availableAmount")
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


    public static String encrypt(String content, String key) {
        try {
            if (key == null || key.length() != 16) {
                return null;
            }
            byte[] dataByte = content.getBytes();
            byte[] keyByte = key.getBytes();
            byte[] ivByte = key.getBytes();
            String encryptedData;
            Security.addProvider(new BouncyCastleProvider());
            //指定算法，模式，填充方式，创建一个 Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding","BC");
            //生成 Key 对象
            SecretKeySpec sKeySpec = new SecretKeySpec(keyByte, "AES");
            //把向量初始化到算法参数
            AlgorithmParameters params = AlgorithmParameters.getInstance("AES");
            params.init(new IvParameterSpec(ivByte));
            //指定用途，密钥，参数 初始化 Cipher 对象
            cipher.init(Cipher.ENCRYPT_MODE, sKeySpec, params);
            //指定加密
            byte[] result = cipher.doFinal(dataByte);
            //对结果进行 Base64 编码，否则会得到一串乱码，不便于后续操作
            Base64.Encoder encoder = Base64.getEncoder(); encryptedData = encoder.encodeToString(result);
            return encryptedData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



    public static String decrypt(String encryptStr, String key) {
        try {
            if (key == null || key.length() != 16) {
                return null;
            }
            //解密之前先把 Base64 格式的数据转成原始格式
            Base64.Decoder decoder = Base64.getDecoder(); byte[] dataByte = decoder.decode(encryptStr); byte[] keyByte = key.getBytes();
            byte[] ivByte = key.getBytes();
            String data;
            Security.addProvider(new BouncyCastleProvider()); //指定算法，模式，填充方法 创建一个 Cipher 实例
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding","BC");
            //生成 Key 对象
            SecretKeySpec sKeySpec = new SecretKeySpec(keyByte, "AES");
            //把向量初始化到算法参数
            AlgorithmParameters params = AlgorithmParameters.getInstance("AES");
            params.init(new IvParameterSpec(ivByte));
            //指定用途，密钥，参数 初始化 Cipher 对象
            cipher.init(Cipher.DECRYPT_MODE, sKeySpec, params);
            //执行解密
            byte[] result = cipher.doFinal(dataByte);
            //解密后转成字符串
            data = new String(result);
            return data;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}