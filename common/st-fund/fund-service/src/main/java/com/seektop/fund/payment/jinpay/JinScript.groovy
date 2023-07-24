package com.seektop.fund.payment.jinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.business.recharge.GlRechargeBusiness
import com.seektop.fund.mapper.GlWithdrawMapper
import com.seektop.fund.model.*
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 金支付
 */
public class JinScript {


    private static final Logger log = LoggerFactory.getLogger(JinScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlRechargeBusiness rechargeBusiness

    private GlWithdrawMapper glWithdrawMapper

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

        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            payType = "5"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "10"
        } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
            payType = "25"
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("不支持充值方式" + merchant.getPaymentName())
            return
        }
        prepareScan(merchant, payment, req, result, payType, args[5])
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType, Object[] args) {
        try {
            Map<String, String> DataContentParms = new LinkedHashMap<>()
            DataContentParms.put("customerNo", payment.getMerchantCode())
            DataContentParms.put("tradeDate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD))
            DataContentParms.put("cusOrderNo", req.getOrderId())
            DataContentParms.put("orderTitle", "CZ")
            DataContentParms.put("payType", payType)
            DataContentParms.put("orderAmt", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString())
            DataContentParms.put("userAcct", req.getUserId().toString())
            DataContentParms.put("userName", req.getFromCardUserName().trim())
            DataContentParms.put("userLevel", "1")
            DataContentParms.put("notifyUrl", payment.getNotifyUrl() + merchant.getId())

            String toSign = JSON.toJSONString(DataContentParms) + payment.getPrivateKey()

            Map<String, String> head = new HashMap<>()
            head.put("customerNo", payment.getMerchantCode())
            head.put("signedMsg", MD5.md5(toSign))

            log.info("JinScript_Prepare_Params:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/golden-web/api/orderPayTransfer/v1", JSON.toJSONString(DataContentParms), head, requestHeader)

            JSONObject json = JSON.parseObject(restr)
            log.info("JinScript_Prepare_resStr:{}", json)

            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (!json.getString("retCode").equals("000000")) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(json.getString("retMsg"))
                return
            }
            JSONObject dataJSON = json.getJSONObject("rows")
            if (dataJSON == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(restr)
                return
            }

            String recBankCode = dataJSON.getString("recBankCode")
            String recAcctName = dataJSON.getString("recAcctName")
            String recAcctNo = dataJSON.getString("recAcctNo")
            String recSubBankName = dataJSON.getString("recSubBankName")

            result.setMessage(dataJSON.getString("cashierUrl"))
            if (StringUtils.isEmpty(recSubBankName)) {
                recSubBankName = dataJSON.getString("recBankName")
            }
            this.glPaymentChannelBankBusiness = BaseScript.getResource(args[0], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
            GlPaymentChannelBank bank = glPaymentChannelBankBusiness.getChannelBank(payment.getChannelId(), recBankCode)

            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            BankInfo bankInfo = new BankInfo();
            bankInfo.setName(recAcctName)
            bankInfo.setBankId(bank.getBankId())
            bankInfo.setBankName(bank.getBankName())
            bankInfo.setBankBranchName(recSubBankName)
            bankInfo.setCardNo(recAcctNo)
            result.setBankInfo(bankInfo)

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
        log.info("JinScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String str = resMap.get("reqBody")
        str = str.trim().replaceAll("\\\\", "")
        JSONObject json = JSONObject.parseObject(str)
        String orderId = json.getString("cusOrderNo")
        if (StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, payment, orderId, args[4])
        }
        return null

    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        this.rechargeBusiness = BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        GlRecharge glRecharge = rechargeBusiness.findById(orderId)

        Map<String, String> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("customerNo", account.getMerchantCode())
        DataContentParms.put("service", "10302")
        DataContentParms.put("cusOrderNo", orderId)
        DataContentParms.put("tradeDate", DateUtils.format(glRecharge.getCreateDate(), DateUtils.YYYY_MM_DD))

        String toSign = JSON.toJSONString(DataContentParms) + account.getPrivateKey()

        Map<String, String> head = new HashMap<>()
        head.put("customerNo", account.getMerchantCode())
        head.put("signedMsg", MD5.md5(toSign))

        log.info("JinScript_Query_reqMap:{}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/golden-web/api/queryPayTradeStatus/v1", JSON.toJSONString(DataContentParms), head, requestHeader)
        log.info("JinScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "000000") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("rows")
        //20-处理中，30-审核中、50支付中、100-交易成功，-100-交易失败
        if (dataJSON != null && dataJSON.getString("status").equals("100")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(dataJSON.getBigDecimal("payAmt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.UP))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(dataJSON.getString("cusOrderNo"))
            pay.setThirdOrderId(dataJSON.getString("orderNo"))
            def rsp = "{\"retCode\":\"000000\",\"retMsg\" :\"成功\"}"
            pay.setRsp(rsp)
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

    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        if (req.getAmount().subtract(req.getFee()).compareTo(BigDecimal.valueOf(30000)) > 0) {
            return withdraw2(merchantAccount, req)
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("customerNo", merchantAccount.getMerchantCode());
        jsonObject.put("tradeDate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD));
        jsonObject.put("cusOrderNo", req.getOrderId());
        jsonObject.put("orderTitle", "TX");
        jsonObject.put("orderAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString());
        jsonObject.put("userAcct", req.getUserId());
        jsonObject.put("userName", req.getName());
        jsonObject.put("userLevel", "1");
        jsonObject.put("recCardType", "1");
        jsonObject.put("recAcctType", "1");
        jsonObject.put("recAcctNo", req.getCardNo())
        jsonObject.put("recAcctName", req.getName());
        jsonObject.put("recBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        jsonObject.put("recBankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        jsonObject.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

        String toSign = jsonObject.toJSONString() + merchantAccount.getPrivateKey()

        Map<String, String> head = new HashMap<>()
        head.put("customerNo", merchantAccount.getMerchantCode())
        head.put("signedMsg", MD5.md5(toSign))

        log.info("JinScript_Transfer_params: {}", jsonObject.toJSONString())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/golden-web/api/singlePay/v1", jsonObject.toJSONString(), head, requestHeader)
        log.info("JinScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(jsonObject))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (json.getString("retCode").equals("000000")) {
            result.setValid(true)
        } else {
            result.setValid(false)
            result.setMessage(json.getString("retMsg"))
        }
        return result
    }

    /**
     * 代付金额超过3W 调用拆单接口
     * @param merchantAccount
     * @param req
     * @return
     * @throws GlobalException
     */
    public WithdrawResult withdraw2(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("customerNo", merchantAccount.getMerchantCode());
        jsonObject.put("tradeDate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD));
        jsonObject.put("cusOrderNo", req.getOrderId());
        jsonObject.put("orderTitle", "TX");
        jsonObject.put("orderAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString());
        jsonObject.put("userAcct", req.getUserId());
        jsonObject.put("userName", req.getName());
        jsonObject.put("userLevel", "1");
        jsonObject.put("recCardType", "1");
        jsonObject.put("recAcctType", "1");
        jsonObject.put("recAcctNo", req.getCardNo())
        jsonObject.put("recAcctName", req.getName());
        jsonObject.put("recBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        jsonObject.put("recBankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        jsonObject.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());

        String toSign = jsonObject.toJSONString() + merchantAccount.getPrivateKey()

        Map<String, String> head = new HashMap<>()
        head.put("customerNo", merchantAccount.getMerchantCode())
        head.put("signedMsg", MD5.md5(toSign))

        log.info("JinScript_Transfer_splitPay_params: {}", jsonObject.toJSONString())
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/golden-web/api/splitPay/v1", jsonObject.toJSONString(), head, requestHeader)
        log.info("JinScript_Transfer_splitPay_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(jsonObject))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        if (json.getString("retCode").equals("000000")) {
            result.setValid(true)
        } else {
            result.setValid(false)
            result.setMessage(json.getString("retMsg"))
        }
        return result
    }


    public WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("JinScript_Notify_resMap:{}", JSON.toJSONString(resMap))

        String str = resMap.get("reqBody")
        str = str.trim().replaceAll("\\\\", "")
        JSONObject json = JSONObject.parseObject(str)
        String orderId = json.getString("cusOrderNo")

        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId, args[3])
        } else {
            return null
        }
    }

    public WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]
        this.glWithdrawMapper = BaseScript.getResource(args[3], ResourceEnum.GlWithdrawMapper) as GlWithdrawMapper
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId)
        if (glWithdraw == null) {
            return null
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("customerNo", merchant.getMerchantCode());
        jsonObject.put("cusOrderNo", orderId);
        jsonObject.put("tradeDate", DateUtils.format(glWithdraw.getCreateDate(), DateUtils.YYYY_MM_DD));

        String toSign = JSON.toJSONString(jsonObject) + merchant.getPrivateKey()

        Map<String, String> head = new HashMap<>()
        head.put("customerNo", merchant.getMerchantCode())
        head.put("signedMsg", MD5.md5(toSign))

        log.info("JinScript_TransferQuery_reqMap:{}", JSON.toJSONString(jsonObject))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/golden-web/api/queryPayTransStatus/v1", JSON.toJSONString(jsonObject), head, requestHeader)
        log.info("JinScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "000000") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("rows")
        WithdrawNotify notify = new WithdrawNotify()
        if (dataJSON != null) {
            notify.setAmount(dataJSON.getBigDecimal("payAmt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(dataJSON.getString("cusOrderNo"))
            notify.setThirdOrderId(dataJSON.getString("orderNo"))
            //10发起、20-处理中、100-交易成功、-100-交易失败
            if (dataJSON.getString("status") == ("100")) {
                notify.setStatus(0)
                def rsp = "{\"retCode\":\"000000\",\"retMsg\" :\"成功\"}"
                notify.setRsp(rsp)
            } else if (dataJSON.getString("status") == ("-100")) {
                notify.setStatus(1)
                def rsp = "{\"retCode\":\"000000\",\"retMsg\" :\"成功\"}"
                notify.setRsp(rsp)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        Map<String, String> DataContentParms = new HashMap<>()
        DataContentParms.put("customerNo", merchantAccount.getMerchantCode())

        String toSign = JSON.toJSONString(DataContentParms) + merchantAccount.getPrivateKey()

        Map<String, String> head = new HashMap<>()
        head.put("customerNo", merchantAccount.getMerchantCode())
        head.put("signedMsg", MD5.md5(toSign))

        log.info("JinScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/golden-web/api/queryBalance/v1", JSON.toJSONString(DataContentParms), head, requestHeader)
        log.info("JinScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "000000") {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("rows")
        if (dataJSON != null) {
            return dataJSON.getBigDecimal("surplusAmt").divide(BigDecimal.valueOf(100)) == null
                    ? BigDecimal.ZERO : dataJSON.getBigDecimal("surplusAmt").divide(BigDecimal.valueOf(100))
        }
        return BigDecimal.ZERO
    }

    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
//        Integer paymentId = args[1] as Integer
//        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
//                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
//                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
//            return true
//        }
//        return false
        return true
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
        return FundConstant.ShowType.DETAIL
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