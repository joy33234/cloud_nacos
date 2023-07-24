    package com.seektop.fund.payment.hongchengpay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
    import com.seektop.common.utils.MD5
    import com.seektop.enumerate.GlActionEnum
    import com.seektop.exception.GlobalException
    import com.seektop.fund.business.GlPaymentChannelBankBusiness
    import com.seektop.fund.model.GlWithdraw
    import com.seektop.fund.model.GlWithdrawMerchantAccount
    import com.seektop.fund.payment.WithdrawNotify
    import com.seektop.fund.payment.WithdrawResult
    import com.seektop.fund.payment.groovy.BaseScript
    import com.seektop.fund.payment.groovy.ResourceEnum
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.util.ObjectUtils

    import java.math.RoundingMode

    /**
 * 鸿成支付
 * @auth joy
 * @date 2021-05-18
 */

    public class HongChengScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(HongChengScript_withdraw.class)

        private OkHttpUtil okHttpUtil


        private RedisService redisService

        private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


        WithdrawResult withdraw(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
            GlWithdraw req = args[2] as GlWithdraw
            this.redisService = BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService
            this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

            WithdrawResult result = new WithdrawResult()

            Map<String, Object> DataContentParms = new LinkedHashMap<>()
            DataContentParms.put("account_id", account.getMerchantCode())
            DataContentParms.put("out_trade_no", req.getOrderId())
            DataContentParms.put("bank_id", req.getCardNo())
            DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParms.put("bank_name", req.getBankName())
            DataContentParms.put("bank_user", req.getName())
            DataContentParms.put("callback_url", account.getNotifyUrl() + account.getMerchantId())
            DataContentParms.put("withdraw_type", "1")

            String toSign = MD5.md5( DataContentParms.get("account_id") + DataContentParms.get("out_trade_no") + DataContentParms.get("bank_id")) + account.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("HongChengScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/server/withdrawal/appwithdrawal", DataContentParms, requestHeader)
            log.info("HongChengScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("code") != "200") {
                result.setValid(false)
                result.setMessage(json.getString("msg"))
                return result
            }
            result.setValid(true)
            result.setMessage(json.getString("成功"))
            return result
        }


        WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            Map<String, String> resMap = args[2] as Map<String, String>
            log.info("HongChengScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

            String orderId = resMap.get("flow_no")
            if (org.apache.commons.lang3.StringUtils.isEmpty(orderId)) {
                JSONObject json = JSON.parseObject(resMap.get("reqBody"))
                orderId = json.getString("flow_no")
            }
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2] as String

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("ddh", orderId)

            log.info("HongChengScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.get(merchant.getPayUrl() + "/server/api/withdrawQuery", DataContentParms, requestHeader)
            log.info("HongChengScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getString("code") != "200" ) {
                return null
            }

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId.toUpperCase())
            //订单状态判断标准：1打款中 2提现已到账 3提现已驳回
            if (json.getString("msg") == "2") {
                notify.setStatus(0)
            } else if (json.getString("msg") == "3") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("id", merchantAccount.getMerchantCode())

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("HongChengScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/server/api/useramount", DataContentParams, requestHeader)
            log.info("HongChengScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (ObjectUtils.isEmpty(responJSON) || responJSON.getString("code") != "200") {
                return BigDecimal.ZERO;
            }
            if (responJSON != null) {
                return responJSON.getBigDecimal("msg") == null ? BigDecimal.ZERO : responJSON.getBigDecimal("msg")
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


    }