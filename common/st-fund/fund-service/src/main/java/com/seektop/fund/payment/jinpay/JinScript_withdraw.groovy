package com.seektop.fund.payment.jinpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.mapper.GlWithdrawMapper
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 金支付 - 新系统
 * @author joy
 * @date 20210107
 */
public class JinScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(JinScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlWithdrawMapper glWithdrawMapper


    public WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        JSONObject paramsJSON = new JSONObject();

        String merchantCode = merchantAccount.getMerchantCode();
        String payAmt = req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0).toString();

        Map<String, String> params = new HashMap<>()
        params.put("customerNo", merchantCode);
        params.put("cusOrderNo", req.getOrderId());
        params.put("modeCode", "2800000011");
        params.put("payAmt", payAmt);
        params.put("recCardType","1");
        params.put("recAcctType","1");
        params.put("recAcctNo",req.getCardNo());
        params.put("recAcctName",req.getName());
        params.put("recBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
        params.put("recBankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        params.put("userIp", req.getIp());
        params.put("userNo", req.getUserId());
        params.put("userName", req.getUsername());
        params.put("level", "1");
        params.put("recAbstract", "withdraw");

        String toSign = MD5.toAscii(params) +  merchantAccount.getPrivateKey()

        //头部请求参数
        JSONObject headJSON = getHeadJSON(merchantCode, toSign, req.getCreateDate(),"P20045", req.getOrderId())

        JSONObject requestJSON = JSONObject.parseObject(JSON.toJSONString(params))

        //请求头部
        Map<String, String> header = new HashMap<>()
        header.put("customerNo", merchantCode)

        paramsJSON.put("head",headJSON)
        paramsJSON.put("request",requestJSON)

        log.info("JinScript_Transfer_params: {}", paramsJSON.toJSONString())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/pfront/pay/process", paramsJSON.toJSONString(), header, requestHeader)
        log.info("JinScript_Transfer_resStr: {}", resStr)
        resStr = resStr.substring(1,resStr.length()-1);
        resStr = resStr.trim().replaceAll("\\\\", "")

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramsJSON))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }
        JSONObject dataJSON = json.getJSONObject("response")
        JSONObject headRspJSON = json.getJSONObject("head")
        if (dataJSON == null || headJSON == null || headRspJSON.getString("retCode") != "000000") {
            result.setValid(false)
            result.setMessage(headRspJSON.getString("retMsg"))
        } else {
            result.setValid(true)
            result.setMessage("代付成功")
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
        JSONObject dataJSON = json.getJSONObject("request")
        String orderId = dataJSON.getString("cusOrderNo")

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
        Map<String, String> params = new HashMap<>()
        params.put("customerNo", merchant.getMerchantCode());
        params.put("cusOrderNo", orderId);
        params.put("tradeDate", DateUtils.format(glWithdraw.getCreateDate(), DateUtils.YYYY_MM_DD));
        String toSign = MD5.toAscii(params) +  merchant.getPrivateKey()

        //头部请求参数
        JSONObject headJSON = getHeadJSON(merchant.getMerchantCode(), toSign, glWithdraw.getCreateDate(), "P30040" ,orderId)

        JSONObject requestJSON = JSONObject.parseObject(JSON.toJSONString(params))

        //请求头部
        Map<String, String> header = new HashMap<>()
        header.put("customerNo", merchant.getMerchantCode())

        JSONObject paramsJSON = new JSONObject();
        paramsJSON.put("head",headJSON)
        paramsJSON.put("request",requestJSON)


        log.info("JinScript_TransferQuery_reqMap:{}", JSON.toJSONString(paramsJSON))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(paramsJSON), header, requestHeader)
        log.info("JinScript_TransferQuery_resStr:{}", resStr)
        resStr = resStr.trim().replaceAll("\\\\", "")
        resStr = resStr.substring(1,resStr.length()-1);
        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("response")
        JSONObject headRspJSON = json.getJSONObject("head")
        if (dataJSON == null || headRspJSON == null || headRspJSON.getString("retCode") != "000000") {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        notify.setAmount(dataJSON.getBigDecimal("payAmt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN))
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(dataJSON.getString("cusOrderNo"))
        notify.setThirdOrderId(dataJSON.getString("orderNo"))
        //5审核中，10-处理中，100-代付成功，110-代付退汇，-100-代付失败
        if (dataJSON.getString("status") == ("100")) {
            notify.setStatus(0)
        } else if (dataJSON.getString("status") == ("-100")
            || dataJSON.getString("status") == ("100")) {
            notify.setStatus(1)
        } else {
            notify.setStatus(2)
        }
        return notify
    }


    public BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount

        Date now = new Date();
        Map<String, String> params = new HashMap<>()
        params.put("customerNo", merchant.getMerchantCode());
        params.put("tradeTime", DateUtils.format(now, DateUtils.YYYY_MM_DD_HH_MM_SS));
        String toSign = MD5.toAscii(params) +  merchant.getPrivateKey()

        //头部请求参数
        JSONObject headJSON = getHeadJSON(merchant.getMerchantCode(), toSign, now,"P30050", "")

        JSONObject requestJSON = JSONObject.parseObject(JSON.toJSONString(params))

        //请求头部
        Map<String, String> header = new HashMap<>()
        header.put("customerNo", merchant.getMerchantCode())

        JSONObject paramsJSON = new JSONObject();
        paramsJSON.put("head",headJSON)
        paramsJSON.put("request",requestJSON)

        log.info("JinScript_QueryBalance_reqMap: {}", JSON.toJSONString(paramsJSON))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(paramsJSON), header, requestHeader)
        log.info("JinScript_QueryBalance_resStr: {}", resStr)
        resStr = resStr.substring(1,resStr.length()-1);
        resStr = resStr.trim().replaceAll("\\\\", "")

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        JSONObject dataJSON = json.getJSONObject("response")
        JSONObject headRspJSON = json.getJSONObject("head")
        if (dataJSON == null || headJSON == null || headRspJSON.getString("retCode") != "000000") {
            return null
        }
        return dataJSON.getBigDecimal("surplusAmt").divide(BigDecimal.valueOf(100)) == null
                    ? BigDecimal.ZERO : dataJSON.getBigDecimal("surplusAmt").divide(BigDecimal.valueOf(100))
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


    private JSONObject getHeadJSON(String merchantCode, String toSign, Date createDate, String trxCode, String orderId) {
        JSONObject headJSON = new JSONObject();
        headJSON.put("customerNo", merchantCode)
        headJSON.put("trxCode", trxCode)
        headJSON.put("version", "01")
        headJSON.put("reqSn", orderId + System.currentTimeMillis().toString())
        headJSON.put("timestamp", DateUtils.format(createDate, DateUtils.YYYYMMDDHHMMSS))
        headJSON.put("signedMsg", MD5.md5(toSign))
        return headJSON;
    }
}