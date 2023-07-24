package com.seektop.fund.payment.joypay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 *
 * @author walter
 */
public class JoyScript {

    private static final Logger log = LoggerFactory.getLogger(JoyScript.class)

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
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[5], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        String service = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            service = "bank"
        }
        if (StringUtils.isNotEmpty(service)) {
            prepareScan(merchant, payment, req, result, service)
        }
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>()
            params.put("fxid", payment.getMerchantCode())
            params.put("fxddh", req.getOrderId())
            params.put("fxdesc", "recharge")
            params.put("fxbankcode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), payment.getChannelId()))
            params.put("fxfee", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            params.put("fxnotifyurl", payment.getNotifyUrl() + merchant.getId())
            params.put("fxbackurl", payment.getNotifyUrl() + merchant.getId())
            params.put("fxpay", service)
            params.put("fxip", req.getIp())
            params.put("fxnotifystyle", "2")//回调返回json数据

            String toSign = payment.getMerchantCode() + req.getOrderId() + params.get("fxfee") + params.get("fxnotifyurl") + payment.getPrivateKey()
            params.put("fxsign", MD5.md5(toSign))

            log.info("JoyScript_Prepare_Params:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode())
            String restr = okHttpUtil.post(payment.getPayUrl() + "/Pay", params, requestHeader)
            log.info("JoyScript_Prepare_resStr:{}", restr)

            JSONObject json = JSON.parseObject(restr)
            //状态【1代表正常】【0代表错误】
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (json.getString("status") != ("1") || StringUtils.isEmpty(json.getString("payurl"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg("商户异常")
                return
            }
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(json.getString("payurl"))
        } catch (Exception e) {
            e.printStackTrace()
        }
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
        log.info("JoyScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("fxddh")
        if (StringUtils.isNotEmpty(orderid)) {
            return payQuery(okHttpUtil, payment, orderid, args[4])
        } else {
            return null
        }
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        Map<String, String> params = new HashMap<String, String>()
        params.put("fxid", account.getMerchantCode())
        params.put("fxaction", "orderquery")
        params.put("fxddh", orderId)

        String toSign = account.getMerchantCode() + orderId + params.get("fxaction") + account.getPrivateKey()
        params.put("fxsign", MD5.md5(toSign))

        log.info("JoyScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay", params, requestHeader)
        log.info("JoyScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        // 支付状态【1正常支付】【0支付异常】
        if ("1" == (json.getString("fxstatus"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("fxfee").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("fxorder"))
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
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("fxid", merchantAccount.getMerchantCode())
        DataContentParms.put("fxaction", "repay")
        DataContentParms.put("fxnotifystyle", "2")//回调返回json数据
        DataContentParms.put("fxnotifyurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        JSONArray array = new JSONArray();
        JSONObject body = new JSONObject();
        body.put("fxddh", req.getOrderId())
        body.put("fxdate", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS))
        body.put("fxfee", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        body.put("fxbody", req.getCardNo())
        body.put("fxname", req.getName())
        body.put("fxaddress", "上海市")//开启行
        array.add(body)
        DataContentParms.put("fxbody", array.toJSONString())


        String toSign = merchantAccount.getMerchantCode() + DataContentParms.get("fxaction") + DataContentParms.get("fxbody") + merchantAccount.getPrivateKey()
        DataContentParms.put("fxsign", MD5.md5(toSign))

        log.info("JoyScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Pay", DataContentParms, requestHeader)
        log.info("JoyScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("fxstatus") != 1) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("fxmsg"))
            return result
        }

        JSONArray respArray = JSONArray.parseArray(json.getString("fxbody"));
        if (null == respArray || respArray.size() == 0) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("fxmsg"))
            return result
        }

        JSONObject jsonBody = respArray.get(0)
        if (jsonBody == null || jsonBody.getInteger("fxstatus") != 1) {
            result.setValid(false)
            result.setMessage(jsonBody == null ? "API异常:请联系出款商户确认订单." : jsonBody.getString("fxcode"))
            return result
        }

        result.setValid(true)
        result.setMessage(jsonBody.getString("fxcode"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("JoyScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderid = json.getString("fxddh")
        if (StringUtils.isNotEmpty(orderid)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, args[3])
        } else {
            return null
        }
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("fxid", merchant.getMerchantCode())
        DataContentParms.put("fxaction", "repayquery")

        JSONArray array = new JSONArray()
        JSONObject body = new JSONObject();
        body.put("fxddh", orderId)
        array.add(body)
        DataContentParms.put("fxbody", array.toJSONString())


        String toSign = merchant.getMerchantCode() + DataContentParms.get("fxaction") + DataContentParms.get("fxbody") + merchant.getPrivateKey()
        DataContentParms.put("fxsign", MD5.md5(toSign))


        log.info("JoyScript_TransferQuery_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Pay", DataContentParms, requestHeader)
        log.info("JoyScript_TransferQuery_resStr:{}", resStr)
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        JSONArray respArray = JSONArray.parseArray(json.getString("fxbody"));
        JSONObject dataJSON = respArray.getJSONObject(0);
        WithdrawNotify notify = new WithdrawNotify()
        //-1:订单不存在  0：正常申请   1：已打款   2：冻结    3：已取消
        if (dataJSON != null && json.getInteger("fxstatus") == 1) {//代付查询状态【0失败】【1成功】
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            if (dataJSON.getInteger("fxstatus") == 1) {
                notify.setStatus(0)
                notify.setRsp("success")
            } else if (dataJSON.getInteger("fxstatus") == 3) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("fxid", merchantAccount.getMerchantCode())
        DataContentParms.put("fxaction", "money")
        DataContentParms.put("fxdate", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))


        String toSign = merchantAccount.getMerchantCode() + DataContentParms.get("fxdate") + DataContentParms.get("fxaction") + merchantAccount.getPrivateKey()
        DataContentParms.put("fxsign", MD5.md5(toSign))

        log.info("JoyScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Pay", DataContentParms, requestHeader)
        log.info("JoyScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json != null) {
            return json.getBigDecimal("fxmoney") == null ? BigDecimal.ZERO : json.getBigDecimal("fxmoney")
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
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.JOY_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JOY_PAY.getPaymentName())
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
