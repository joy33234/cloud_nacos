package com.seektop.fund.payment.siJiPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.constant.ProjectConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
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
 * @desc 四季支付
 * @date 2021-12-03
 * @auth Otto
 */
public class SiJiScript_recharge {


    private static final Logger log = LoggerFactory.getLogger(SiJiScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        String payType = ""
        if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            payType = "1"//支付宝转帐，
        } else if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "0"//卡卡
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("userId", req.getUserId().toString())
        params.put("orderId", req.getOrderId())
        params.put("orderTime", DateUtils.format(new Date(), DateUtils.YYYY_MM_DD_HH_MM_SS))
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("payer", req.getFromCardUserName())
        params.put("payWith", payType)
        params.put("useCounter", "false")
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("nonce", MD5.md5(req.getOrderId()).substring(0, 9))
        params.put("timestamp", System.currentTimeMillis().toString())
        if (req.getClientType() == ProjectConstant.ClientType.PC) {
            params.put("terminalType", "PC")
        } else {
            params.put("terminalType", "MOB")
        }

        JSONObject jsonParams = new JSONObject();
        jsonParams.put("id", account.getMerchantCode());
        jsonParams.put("data", encrypt(JSON.toJSONString(params), account.getPrivateKey()))

        log.info("SiJiScript_recharge_prepare_params:{} , url:{}", JSON.toJSONString(params), account.getPayUrl())
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/biz/place_deposit_order", JSON.toJSONString(jsonParams), requestHeader)
        resStr = decrypt(resStr, account.getPrivateKey())
        log.info("SiJiScript_recharge_prepare_resp:{} , orderId:{}", resStr, req.getOrderId())
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null || "0" != json.getString("code")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }
        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(json.getString("holder"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(json.getString("bank"))
        bankInfo.setCardNo(json.getString("account"))
        result.setBankInfo(bankInfo)
        result.setThirdOrderId(json.getString("transId"))
    }


    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        JSONObject json = JSON.parseObject(resMap.get("reqBody"));
        String data = json.getString("data")
        data = decrypt(data, account.getPrivateKey())
        JSONObject dataJSON = JSONObject.parseObject(data);
        String orderId = dataJSON.getString("orderId")
        log.info("SiJiScript_notify_resp:{} ,orderId:{}", JSON.toJSONString(resMap), orderId)
        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchantId", account.getMerchantCode())
        params.put("orderId", orderId)
        params.put("nonce", MD5.md5(orderId).substring(0, 9))
        params.put("timestamp", System.currentTimeMillis().toString())

        JSONObject jsonParams = new JSONObject();
        jsonParams.put("id", account.getMerchantCode());
        jsonParams.put("data", encrypt(JSON.toJSONString(params), account.getPrivateKey()))

        log.info("SiJiScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/api/biz/query_deposit_order", JSON.toJSONString(jsonParams), requestHeader)
        resStr = decrypt(resStr, account.getPrivateKey())
        log.info("SiJiScript_query_resp:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "0") {
            return null
        }
        // 支付状态:status 订单状态 整数型:0 为订单成功完成，非 0 为其他状态
        if ("0" == (json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
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
        return true
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
        return FundConstant.ShowType.DETAIL
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
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
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
            log.info("SiJiScript_recharge_prepare_params: 加密失败")
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
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] dataByte = decoder.decode(encryptStr);
            byte[] keyByte = key.getBytes();
            byte[] ivByte = key.getBytes();
            String data = "";
            Security.addProvider(new BouncyCastleProvider()); //指定算法，模式，填充方法 创建一个 Cipher 实例
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
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
            log.info("SiJiScript_notify_resp: 解密失败")
            e.printStackTrace();
        }
        return null;
    }


}