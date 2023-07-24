package com.seektop.fund.payment.hiltonpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 希尔顿支付
 */

public class HiltonScript {
    private static final Logger log = LoggerFactory.getLogger(HiltonScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult

        Map<String, String> params = new HashMap<String, String>()
        params.put("merchant_id", payment.getMerchantCode())
        params.put("out_trade_no", req.getOrderId())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())

        String sign = payment.getPrivateKey() + req.getOrderId() + params.get("amount")
        params.put("sign", MD5.md5(sign))

        log.info("HiltonScript_Prepare_resMap:{}", JSON.toJSONString(params))

        String paramsStr = MD5.toSign(params);
        String prepareUrl = payment.getPayUrl() + "/api/createOrder" + "?" + paramsStr;
        log.info("HiltonScript_Prepare_prepareUrl:{}", prepareUrl)
        result.setRedirectUrl(prepareUrl);
    }


    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HiltonScript_Notify_resMap:{}", JSON.toJSONString(resMap))


        JSONObject json = JSONObject.parseObject(JSON.toJSONString(resMap));
        log.info(json.toJSONString());


        for (Map.Entry entry : json.entrySet()) {
            if (entry.getKey() != "reqBody") {
                JSONObject dataJSON = JSONObject.parseObject(entry.getKey().toString());
                String orderId = dataJSON.getString("out_trade_no");
                String amount = dataJSON.getString("amount");
                log.info(orderId + "" + amount);
                return this.payQuery(okHttpUtil, payment, orderId + "||" + amount)
            }
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        String[] arr = orderId.split("\\|\\|")
        Map<String, String> DataContentParms = new HashMap<String, String>()
        DataContentParms.put("out_trade_no", arr[0])

        log.info("HiltonScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", arr[0], GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/getOrder", DataContentParms, requestHeader)
        log.info("HiltonScript_Query_resStr:{},{}", resStr, arr[0])

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("code") != "200") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("data")
        // 订单状态判断标准:1为待支付、2为支付成功
        if (dataJSON != null && "2" == dataJSON.getString("status")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(new BigDecimal(arr[1]))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(arr[0])
            pay.setThirdOrderId("")
            pay.setRsp("ok")
            return pay
        }
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("merchant_id", account.getMerchantCode())
        DataContentParms.put("order_sn", req.getOrderId())
        DataContentParms.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bankaccount", req.getCardNo())
        DataContentParms.put("bankname", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        DataContentParms.put("realname", req.getName())
        DataContentParms.put("bankaddress", "上海市")
        DataContentParms.put("phone", "13611111111")

        String toSign = account.getMerchantCode() + account.getPrivateKey() + account.getPublicKey();
        DataContentParms.put("sign", MD5.md5(toSign))
        DataContentParms.put("notify_url", account.getNotifyUrl() + account.getMerchantId())


        log.info("HiltonScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/index.php/api/withdraw", DataContentParms, requestHeader)
        log.info("HiltonScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "200" != json.getString("code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"))
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
        log.info("HiltonScript_withdraw_Notify_resMap:{}", JSON.toJSONString(resMap))

        JSONObject json = JSONObject.parseObject(JSON.toJSONString(resMap));
        for (Map.Entry entry : json.entrySet()) {
            if (entry.getKey() != "reqBody") {
                JSONObject dataJSON = JSONObject.parseObject(entry.getKey().toString());
                String orderId = dataJSON.getString("order_sn");
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("order_sn", orderId)


        log.info("HiltonScript_TransferQuery_order:{}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/index.php/api/getWithdraw", DataContentParms, requestHeader)
        log.info("HiltonScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("code").intValue() != 200) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        JSONObject dataJSON = json.getJSONObject("data")
        if (dataJSON != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataJSON.getString("order_sn"))
            notify.setThirdOrderId("")
            if (dataJSON.getInteger("status") == 1) {//订单状态判断标准：status为0待处理，1已放行 2已拒绝
                notify.setStatus(0)
                notify.setRsp("ok")
            } else if (dataJSON.getInteger("status") == 2) {
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
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> DataContentParams = new HashMap<>()
        DataContentParams.put("merchant_id", merchantAccount.getMerchantCode())

        String toSign = merchantAccount.getMerchantCode() + merchantAccount.getPrivateKey() + merchantAccount.getPublicKey();
        DataContentParams.put("sign", MD5.md5(toSign))

        log.info("HiltonScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/index.php/api/getMerchantWallet", DataContentParams, requestHeader)
        log.info("HiltonScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null && "200" == json.getString("code")) {
            JSONObject dataJSON = json.getJSONObject("data")
            return dataJSON.getBigDecimal("wallet") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("wallet")
        }
        return BigDecimal.ZERO
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
}