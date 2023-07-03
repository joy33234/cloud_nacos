package com.seektop.fund.payment.miaoFuv2

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
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
import java.nio.charset.StandardCharsets
/**
 * @desc 淼富支付V2
 * @date 2021-12-01
 * @auth Redan
 */
public class MiaoFuv2Script_recharge {

    private static final Logger log = LoggerFactory.getLogger(MiaoFuv2Script_recharge.class)

    private OkHttpUtil okHttpUtil

    public class HCPayUtils {

        // 大写
        public static String hex(byte[] data) {
            return Hex.encodeHexString(data, false);
        }

        public static byte[] md5(String data) {
            return DigestUtils.md5(data.getBytes(StandardCharsets.UTF_8));
        }

        public static String sign(String data) {
            return hex(md5(data));
        }

        public static String sign(TreeMap<String, String> data, String key) {
            Set<String> names = data.keySet();
            StringBuffer sb = new StringBuffer();
            for (String name : names) {
                sb.append(name).append("=").append(data.get(name)).append("&");
            }
            sb.append("key=").append(key);
            return sign(sb.toString());
        }
    }

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
        /*
        1: 微信扫码
        2: 支付宝
        6: 银行卡转银行卡* 20211128目前只有這個
        **/
        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            payType = "6"//支付宝转帐，
        }
        if (StringUtils.isEmpty(payType)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, payType)
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new TreeMap<>()
        params.put("merchant", account.getMerchantCode())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("paymentType", payType)
        params.put("username", req.getUsername())
        params.put("depositRealname", req.getFromCardUserName())
        params.put("callback", account.getNotifyUrl() + merchant.getId())
        params.put("paymentReference", req.getOrderId())
        params.put("sign", HCPayUtils.sign(params,account.getPrivateKey()))

        log.info("MiaoFuv2Script_recharge_prepare_params:{} , url:{}", params )
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/deposit/page", params, requestHeader)


        log.info("MiaoFuv2Script_recharge_prepare_resp:{} , orderId:{}", resStr , req.getOrderId())
        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        JSONObject json = JSONObject.parseObject(resStr)

        if (json == null || ObjectUtils.isEmpty(json.getJSONObject("data")) || !json.getBoolean("success")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("message"))
            return
        }
        JSONObject dataJSON = json.getJSONObject("data");
        result.setRedirectUrl(dataJSON.getString("redirect"))

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("MiaoFuv2Script_notify_resp:{}", resMap)
        String orderId = resMap.get("paymentReference")

        if (StringUtils.isNotEmpty(orderId)) {
            return this.payQuery(okHttpUtil, account, orderId, args[4])
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new TreeMap<>()
        params.put("merchant", account.getMerchantCode())
        params.put("paymentReference", orderId)
        params.put("sign", HCPayUtils.sign(params,account.getPrivateKey()))

        log.info("MiaoFuv2Script_query_params:{}", params)
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/deal/query", params, requestHeader)
        log.info("MiaoFuv2Script_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (json == null || ObjectUtils.isEmpty(json.getJSONObject("data"))) {
            return null
        }
        if (!json.getBoolean("success")) {
            return null
         }

        JSONObject dataJson = json.getJSONObject("data")

        //订单状态。0，待处理。1，成功。2，失败。
        if (dataJson.getInteger("statusSt") ==  1 ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJson.getBigDecimal("revisedPrice").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("{\"success\":\"true\"}")
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
}