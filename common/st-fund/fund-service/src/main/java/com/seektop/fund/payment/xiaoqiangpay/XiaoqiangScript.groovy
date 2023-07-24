package com.seektop.fund.payment.xiaoqiangpay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.business.withdraw.GlWithdrawBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 小强支付
 */

public class XiaoqiangScript {
    private static final Logger log = LoggerFactory.getLogger(XiaoqiangScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    private GlWithdrawBusiness glWithdrawBusiness


    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {

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
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
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

        Map<String, String> request = new HashMap<>()
        request.put("customerNo", account.getMerchantCode())
        request.put("cusOrderNo", req.getOrderId())
        request.put("tradeType", "50") //45单一代付 50余额代付 56理财代付
        request.put("modeCode", "2800000011") //支付模式
        request.put("payAmt", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN).toString())
        request.put("recAcctType", "1")
        request.put("recAcctNo", req.getCardNo())
        request.put("recAcctName", req.getName())
        request.put("recBankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        request.put("recBankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
        request.put("remark", "withdraw")
        request.put("recAbstract", "withdraw")
        request.put("notifyUrl", account.getNotifyUrl() + account.getMerchantId())


        Map<String, String> head = new LinkedHashMap<>()
        head.put("trxCode", "P20045")//报文交易码
        head.put("customerNo", account.getMerchantCode())
        head.put("version", "01")
        head.put("timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        head.put("reqSn", head.get("trxCode") + head.get("timestamp") + (long) (Math.random() * 100000))

        String toSign = MD5.toAscii(request) + account.getPrivateKey()
        head.put("signedMsg", MD5.md5(toSign))

        Map<String, Object> params = new HashMap<>()
        params.put("head", head)
        params.put("request", request)

        Map<String, String> header = new HashMap<>()
        header.put("customerNo", account.getMerchantCode())
        header.put("Content-Type", "application/json")

        log.info("XiaoqiangScript_transfer_params:{}", JSON.toJSONString(params))

        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.WITHDRAW.getCode())
                .channelId(account.getChannelId() + "")
                .channelName(account.getChannelName())
                .userId(req.getUserId().toString())
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build()
        String retBack = okHttpUtil.postJSON(account.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(params), header, requestHeader)
        log.info("XiaoqiangScript_transfer_result:{}", retBack)

        WithdrawResult result = new WithdrawResult()
        retBack = retBack.trim().replaceAll("\\\\", "")
        retBack = retBack.substring(1, retBack.length() - 1)
        RspMsg rspMsg = JSON.parseObject(retBack, RspMsg.class)
        JSONObject headJSON = JSONObject.parseObject(rspMsg.getHead().toString());
        result.setOrderId(req.getOrderId())
        result.setReqData(params.toString())
        result.setResData(retBack)

        if (null == headJSON || "000000" != headJSON.getString("retCode")) {
            result.setValid(false)
            result.setMessage(headJSON == null ? "API异常:请联系出款商户确认订单." : headJSON.getString("retMsg"))
            log.info(JSON.toJSONString(result))
            return result
        }
        result.setValid(true)
        result.setMessage(headJSON.getString("retMsg"))
        log.info(JSON.toJSONString(result))
        return result
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("XiaoqiangScript_Notify_resMap:{}", JSON.toJSONString(resMap))

        String resStr = resMap.get("reqBody")
        resStr = resStr.trim().replaceAll("\\\\", "")
        RspMsg rspMsg = JSON.parseObject(resStr, RspMsg.class);

        JSONObject reqJSON = JSONObject.parseObject(rspMsg.getRequest().toString())

        String orderId = reqJSON.getString("orderNo");//三方商户平台订单号
        if (StringUtils.isNotEmpty(orderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderId)
        }
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> request = new HashMap<>()
        request.put("customerNo", account.getMerchantCode())
        request.put("orderNo", orderId)

        Map<String, String> head = new HashMap<>()
        head.put("trxCode", "P30040")//报文交易码
        head.put("customerNo", account.getMerchantCode())
        head.put("version", "01")
        head.put("timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        head.put("reqSn", head.get("trxCode") + head.get("timestamp") + (long) (Math.random() * 100000))
        String toSign = MD5.toAscii(request) + account.getPrivateKey()
        head.put("signedMsg", MD5.md5(toSign))

        Map<String, Object> params = new HashMap<>()
        params.put("head", head)
        params.put("request", request)

        Map<String, String> header = new HashMap<>()
        header.put("customerNo", account.getMerchantCode())
        header.put("Content-Type", "application/json")


        log.info("XiaoqiangScript_TransferQuery_order:{}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(params), header, requestHeader)
        log.info("XiaoqiangScript_TransferQuery_resStr:{}", resStr)

        resStr = resStr.trim().replaceAll("\\\\", "")
        resStr = resStr.substring(1, resStr.length() - 1)
        RspMsg rspMsg = JSON.parseObject(resStr, RspMsg.class);

        if (rspMsg == null) {
            return null
        }
        JSONObject headJSON = JSONObject.parseObject(rspMsg.getHead().toString())
        JSONObject resJSON = JSONObject.parseObject(rspMsg.getResponse().toString())
        WithdrawNotify notify = new WithdrawNotify()
        if (headJSON != null && resJSON != null && headJSON.getString("retCode") == "000000") {
            notify.setMerchantCode(account.getMerchantCode())
            notify.setMerchantId(account.getMerchantId())
            notify.setMerchantName(account.getChannelName())
            notify.setOrderId(resJSON.getString("cusOrderNo"))
            notify.setThirdOrderId(resJSON.getString("orderNo"))
            if (resJSON.getString("status") == "100") {//代付状态  10-已受理，100-代付成功，110-代付退汇，-100-代付失败
                notify.setStatus(0)
            } else if (resJSON.getString("status") == "-100") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
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

class RspMsg {

    /** 相应报文头 head */
    private Object head;
    /** 相应报文体 response */
    private Object response;
    /** 相应报文体 request */
    private Object request;

    public RspMsg() {
    }

    public RspMsg(Object head, Object response) {
        this.head = head;
        this.response = response;
    }

    public Object getHead() {
        return head;
    }

    public void setHead(Object head) {
        this.head = head;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public Object getRequest() {
        return request;
    }

    public void setRequest(Object request) {
        this.request = request;
    }
}
