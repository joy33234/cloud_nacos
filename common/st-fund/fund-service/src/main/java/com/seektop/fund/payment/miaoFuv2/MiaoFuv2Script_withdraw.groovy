package com.seektop.fund.payment.miaoFuv2

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
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
import java.nio.charset.StandardCharsets

/**
 * @desc 淼富支付v2
 * @date 2021-12-01
 * @auth Redan
 */
public class MiaoFuv2Script_withdraw {

    private static final Logger log = LoggerFactory.getLogger(MiaoFuv2Script_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

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

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new TreeMap<>()
        params.put("merchant", account.getMerchantCode())
        params.put("requestReference", req.getOrderId())
        params.put("merchantBank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        params.put("merchantBankCardRealname", req.getName())
        params.put("merchantBankCardAccount", req.getCardNo())
        params.put("merchantBankCardProvince", "上海市")
        params.put("merchantBankCardCity", "上海市")
        params.put("merchantBankCardBranch", "上海市")
        params.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("callback", account.getNotifyUrl() + account.getMerchantId())
        params.put("sign", HCPayUtils.sign(params,account.getPrivateKey()))

        log.info("MiaoFuPay_doTransfer_params:{} , url:{}", params , account.getPayUrl())
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/merchant/withdraw", params, requestHeader)
        log.info("MiaoFuPay_doTransfer_resp:{} , ordedId:{}", resStr , req.getOrderId() )
        JSONObject json = JSON.parseObject(resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)
        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        if (!json.getBoolean("success")) {
            result.setValid(false)
            result.setMessage(json.getString("message"))
            return result
        }
        req.setMerchantId(account.getMerchantId())
        result.setValid(true)
        result.setMessage("ok")
        return result
    }

    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("MiaoFuPay_doTransferNotify_resp:{}", resMap)
        String orderId = resMap.get("requestReference")
        if (StringUtils.isNotEmpty(orderId)) {
            return this.withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        }
        return null
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> params = new TreeMap<>()
        params.put("merchant", merchant.getMerchantCode())
        params.put("requestReference", orderId)
        params.put("sign", HCPayUtils.sign(params,merchant.getPrivateKey()))

        log.info("MiaoFuPay_doTransferQuery_params:{}", params)
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/merchant/withdraw/query", params, requestHeader)
        log.info("MiaoFuPay_doTransferQuery_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }
        JSONObject json = JSON.parseObject(resStr)
        if (!json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON == null) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("amount"))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setThirdOrderId(dataJSON.getString("reference"))

             /* PENDING，待处理。
                LOCKED，在处理。
                AUDITED，已支付。
                REFUSED，已拒绝。  */
        if (dataJSON.getString("status") == ("PENDING")||dataJSON.getString("status") == ("LOCKED")) {
            notify.setStatus(2)
            notify.setRsp("{\"success\":\"true\"}")
        } else if (dataJSON.getString("status") == ("AUDITED")) {
            notify.setStatus(0)
            notify.setRsp("{\"success\":\"true\"}")
        } else {
            notify.setStatus(1)
            notify.setRsp("{\"success\":\"true\"}")
        }
        return notify
    }

    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> params = new TreeMap<>()
        params.put("merchant", merchantAccount.getMerchantCode())
        params.put("sign", HCPayUtils.sign(params,merchantAccount.getPrivateKey()))

        log.info("MiaoFuPay_Query_Balance_Params: {}", params)
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/merchant/info", params,  requestHeader)
        log.info("MiaoFuPay_Query_Balance_resStr: {}", resStr)

        if (StringUtils.isEmpty(resStr)) {
            return BigDecimal.ZERO
        }
        JSONObject json = JSON.parseObject(resStr)
        if (!json.getBoolean("success")) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data");
        if (dataJSON == null) {
            return BigDecimal.ZERO
        }
        BigDecimal balance = dataJSON.getBigDecimal("wallet")
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
}