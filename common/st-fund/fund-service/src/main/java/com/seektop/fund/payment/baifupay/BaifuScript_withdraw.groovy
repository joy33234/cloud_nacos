    package com.seektop.fund.payment.baifupay

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
 * 百富支付
 * @auth joy
 * @date 2021-05-18
 */

    public class BaifuScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(BaifuScript_withdraw.class)

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

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("accountnumber", req.getCardNo())
            DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParms.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
            DataContentParms.put("branch", "any")
            DataContentParms.put("callback_url", account.getNotifyUrl() + account.getMerchantId())
            DataContentParms.put("merchant_code", account.getMerchantCode())
            DataContentParms.put("name", req.getName())
            DataContentParms.put("order_id", req.getOrderId())

            DataContentParms.put("timestamp", (int)(System.currentTimeMillis() / 1000) + "")
            String toSign = MD5.toAscii(DataContentParms) + "&" + account.getPrivateKey()

            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("BaifuScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/v4/merchant/withdraw", JSON.toJSONString(DataContentParms), requestHeader)
            log.info("BaifuScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (!json.getBoolean("status")) {
                result.setValid(false)
                result.setMessage(json.getString("message"))
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
            log.info("BaifuScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            JSONObject json = JSON.parseObject(resMap.get("reqBody"))

            String orderId = json.getString("order_id")
            String thirdOrderId = json.getString("withdrawal_id")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]
            String thirdOrderId = args[3]

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("merchant_code", merchant.getMerchantCode())
            DataContentParms.put("order_id", orderId)
            DataContentParms.put("withdrawal_id", thirdOrderId)

            String toSign = MD5.toAscii(DataContentParms) + "&" + merchant.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("BaifuScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/v4/merchant/withdraw/query", JSON.toJSONString(DataContentParms), requestHeader)
            log.info("BaifuScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || !json.getBoolean("status") ) {
                return null
            }
            JSONObject dataJSON = json.getJSONArray("data").get(0);

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId.toUpperCase())
            //订单状态判断标准："REJECTED/APPROVED/PENDING/DISPENSED"
            if (dataJSON.getString("status") == "DISPENSED") {
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == "REJECTED") {
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
            DataContentParams.put("merchant_code", merchantAccount.getMerchantCode())
            DataContentParams.put("time", (int)(System.currentTimeMillis() / 1000) + "")

            String toSign = MD5.toAscii(DataContentParams) + "&" + merchantAccount.getPrivateKey()
            DataContentParams.put("sign", MD5.md5(toSign))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("BaifuScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.postJSON(merchantAccount.getPayUrl() + "/v4/merchant/balance", JSON.toJSONString(DataContentParams), requestHeader)
            log.info("BaifuScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (ObjectUtils.isEmpty(responJSON) || !responJSON.getBoolean("status")) {
                return BigDecimal.ZERO;
            }
            JSONObject dataJSON = responJSON.getJSONArray("data").get(0);
            if (dataJSON != null) {
                return dataJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("balance")
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