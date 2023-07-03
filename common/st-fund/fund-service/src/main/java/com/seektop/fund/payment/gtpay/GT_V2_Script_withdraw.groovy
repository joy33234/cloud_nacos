    package com.seektop.fund.payment.gtpay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONArray
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
    import com.seektop.common.utils.MD5
    import com.seektop.constant.DateConstant
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
     * GT支付 - 20210222
     */

    public class GT_V2_Script_withdraw {
        private static final Logger log = LoggerFactory.getLogger(GT_V2_Script_withdraw.class)

        private OkHttpUtil okHttpUtil


        private static final String query_url = "/GateWay.ashx";

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
            DataContentParms.put("service", "withdraw")
            DataContentParms.put("merchantID", account.getMerchantCode())
            DataContentParms.put("accountName", req.getName())
            DataContentParms.put("accountNo", req.getCardNo())
            DataContentParms.put("bankAddress", "上海市")
            DataContentParms.put("bankCity", "上海市")
            DataContentParms.put("bankNo", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
            DataContentParms.put("bankProvince", "上海市")
            JSONObject groupJSON = getBalanceObj(account)
            if (ObjectUtils.isEmpty(groupJSON)) {
                result.setValid(false)
                result.setMessage("获取groupId异常，请联系技术人员")
                return result
            }
            DataContentParms.put("groupID", groupJSON.getString("GroupID"))
            DataContentParms.put("orderNo", req.getOrderId())
            DataContentParms.put("tradeAmt", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())

            String toSign = MD5.toSign(DataContentParms) + account.getPrivateKey();
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("GT_V2_Script_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + query_url, DataContentParms, 60L, requestHeader)
            log.info("GT_V2_Script_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if ("0" != json.getString("errcode") || StringUtils.isEmpty(json.getString("tradeno"))) {
                result.setValid(false)
                result.setMessage(json.getString("errmsg"))
                return result
            }

            result.setValid(true)
            result.setMessage(json.getString("成功"))
            result.setThirdOrderId(json.getString("tradeno"))
            return result
        }


        WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            Map<String, String> resMap = args[2] as Map<String, String>
            log.info("GT_V2_Script_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            String orderId = resMap.get("orderno")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> DataContentParms = new LinkedHashMap<>()
            DataContentParms.put("service", "withdrawquery")
            DataContentParms.put("merchantID", merchant.getMerchantCode())
            DataContentParms.put("orderNo", orderId)

            String toSign = MD5.toSign(DataContentParms) + merchant.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign))

            log.info("GT_V2_Script_TransferQuery_order:{}", JSON.toJSONString(orderId))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + query_url, DataContentParms, 60L, requestHeader)
            log.info("GT_V2_Script_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getInteger("errcode").intValue() != 0 || StringUtils.isEmpty(json.getString("tradeno"))) {
                return null
            }

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(json.getString("tradeno"))
            //订单状态判断标准：0代表审核中、1代表已审核、2代表已拒绝、3代表已下发、4代表处理中
            if (json.getInteger("tradestatus") == 3) {
                notify.setStatus(0)
            } else if (json.getInteger("tradestatus") == 2) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
            this.redisService = BaseScript.getResource(args[2], ResourceEnum.RedisService) as RedisService

            JSONObject json = getBalanceObj(merchantAccount)
            if (json != null ) {
                return json.getBigDecimal("Balance") == null ? BigDecimal.ZERO : json.getBigDecimal("Balance")
            }
            return BigDecimal.ZERO
        }

        private JSONObject getBalanceObj(GlWithdrawMerchantAccount merchantAccount) {

            String key  = String.format("QUERY_BALANCE_CHANNEL_%s_MERCHANTCODE_%s",merchantAccount.getChannelId(),merchantAccount.getMerchantCode())
            JSONObject json  = redisService.get(key, JSONObject.class);
            if (json != null) {
                return json
            }

            Map<String, String> DataContentParams = new LinkedHashMap<>()
            DataContentParams.put("service", "balance")
            DataContentParams.put("merchantID", merchantAccount.getMerchantCode())
            String toSign = MD5.toSign(DataContentParams) +  merchantAccount.getPrivateKey()
            DataContentParams.put("sign", MD5.md5(toSign))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("GT_Script_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + query_url, DataContentParams, 60L, requestHeader)
            log.info("GT_Script_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (responJSON != null && "0" != responJSON.getString("errcode")) {
                return null
            }

            JSONArray jsonArray = responJSON.getJSONArray("groupList")
            if (jsonArray.size() <= 0) {
                return null
            }
            redisService.set(key, jsonArray.get(0).toString() , DateConstant.MILLISECOND.ONE_MINUTE)
            return jsonArray.get(0);
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