package com.seektop.fund.payment.fangzhoupay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.recharge.GlRechargeBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlRecharge
import com.seektop.fund.payment.BlockInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 方舟支付（USDT）
 * @date 20201212
 * @author joy
 *
 */
class FangzhouScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(FangzhouScript_recharge.class)

    private OkHttpUtil okHttpUtil

    private GlRechargeBusiness rechargeBusiness;

    private static final String url = "/bitpay-gateway/txn";

    private static final BigDecimal rechargeRate = new BigDecimal(6.6)

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        prepareScan(merchant, payment, req, result)
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {

        Map<String, String> params = new HashMap<>();
        params.put("txnType", "01")
        params.put("txnSubType", "81")
        params.put("secpVer", "icp3-1.1")
        params.put("secpMode", "perm")
        params.put("macKeyId", payment.getMerchantCode())
        params.put("merId", payment.getMerchantCode())
        params.put("userId", req.getOrderId())//兼容三方渠道流程，订单号代替用户id
        params.put("exCurrencyCode", "USDT")
        params.put("timeStamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        String toSign = MD5.toAscii(params) + "&k="+ payment.getPrivateKey()
        params.put("mac", MD5.md5(toSign))
        log.info("FangzhouUSDTScript_Prepare_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + url, params, requestHeader)
        JSONObject json = JSON.parseObject(restr)
        log.info("FangzhouUSDTScript_Prepare_Resp = {}", json)
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getString("respCode") != "0000") {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("respMsg"))
            return
        }

        BlockInfo blockInfo = new BlockInfo()
        blockInfo.setDigitalAmount(req.getAmount().divide(rechargeRate, 2, RoundingMode.DOWN))
        blockInfo.setProtocol(req.getProtocol())
        blockInfo.setBlockAddress(json.getString("paymentAddress"))
        blockInfo.setRate(rechargeRate)
        result.setBlockInfo(blockInfo)
    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("FangzhouUSDTScript_Notify_resMap = {}", JSON.toJSONString(resMap))
        String orderId = resMap.get("userId")
        String thirdOrderId = resMap.get("txnId")
        if (StringUtils.isNotEmpty(thirdOrderId) && StringUtils.isNotEmpty(orderId)) {
            return payQuery(okHttpUtil, payment, thirdOrderId, orderId , args[4])
        }
        return null
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[1] as GlPaymentMerchantaccount
        String thirdOrderId = args[2] as String
        String orderId = args[3] as String

        this.rechargeBusiness = BaseScript.getResource(args[4], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
        GlRecharge recharge = rechargeBusiness.findById(orderId)
        if (recharge == null) {
            return  null
        }

        Map<String, String> params = new HashMap<>();
        params.put("txnType", "00")
        params.put("txnSubType", "11")
        params.put("secpVer", "icp3-1.1")
        params.put("secpMode", "perm")
        params.put("macKeyId", payment.getMerchantCode())
        params.put("merId", payment.getMerchantCode())
        params.put("txnId", thirdOrderId)
        params.put("orderDate", DateUtils.format(recharge.getCreateDate(), DateUtils.YYYYMMDD))
        params.put("timeStamp",  DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))


        String toSign = MD5.toAscii(params) + "&k="+ payment.getPrivateKey()
        params.put("mac", MD5.md5(toSign))

        log.info("FangzhouUSDTScript_Query_Params = {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), payment.getChannelId(), payment.getChannelName())
        String result = okHttpUtil.post(payment.getPayUrl() + url, params , requestHeader)
        log.info("FangzhouUSDTScript_Query_resStr = {}", result)

        JSONObject json = JSON.parseObject(result)
        if (json == null || ("0000" != json.getString("respCode")  && "1101" != json.getString("respCode"))) {
            return null
        }

        //交易状态 01---处理中 10---交易成功 20---交易失败 30---其他状态(需联系管理人员)
        if (json == null || json.getInteger("txnStatus") != 10) {
            return null
        }
        RechargeNotify pay = new RechargeNotify()
        pay.setAmount(json.getBigDecimal("exAmount").multiply(rechargeRate).setScale(2, RoundingMode.DOWN))
        pay.setFee(BigDecimal.ZERO)
        pay.setOrderId(orderId)
        pay.setThirdOrderId(thirdOrderId)
        pay.setRsp("OK")
        return pay
    }


    void cancel(Object[] args) throws GlobalException {

    }


    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, int channelId, String channelName) {
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
        return FundConstant.ShowType.DIGITAL
    }
}