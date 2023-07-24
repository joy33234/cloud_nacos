    package com.seektop.fund.payment.jinpay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.utils.DateUtils
    import com.seektop.common.utils.MD5
    import com.seektop.constant.FundConstant
    import com.seektop.constant.ProjectConstant
    import com.seektop.enumerate.GlActionEnum
    import com.seektop.exception.GlobalException
    import com.seektop.fund.business.recharge.GlRechargeBusiness
    import com.seektop.fund.model.GlPaymentMerchantApp
    import com.seektop.fund.model.GlPaymentMerchantaccount
    import com.seektop.fund.model.GlRecharge
    import com.seektop.fund.payment.BankInfo
    import com.seektop.fund.payment.GlRechargeResult
    import com.seektop.fund.payment.RechargeNotify
    import com.seektop.fund.payment.RechargePrepareDO
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
    public class JinScript_recharge {


        private static final Logger log = LoggerFactory.getLogger(JinScript_recharge.class)

        private OkHttpUtil okHttpUtil

        private GlRechargeBusiness rechargeBusiness

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
            String modeCode = ""
            String txCode = ""
            if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
                payType = "65"
                modeCode = "1650000010"
                txCode = "P10040"
            } else if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
                payType = "20"
                modeCode = "1200000013"
                txCode = "P10010"
            } else {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("不支持充值方式" + merchant.getPaymentName())
                return
            }
            prepareScan(merchant, payment, req, result, payType, modeCode, txCode, args[5])
        }

        public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType, String modeCode, String txCode, Object[] args) {
            JSONObject paramsJSON = new JSONObject();

            String merchantCode = payment.getMerchantCode();

            Map<String, String> params = new HashMap<>()
            params.put("customerNo", merchantCode);
            params.put("cusOrderNo", req.getOrderId());
            params.put("payType", payType);
            params.put("modeCode", modeCode);
            params.put("payAmt", req.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0).toString());
            params.put("orderTitle", "recharge");
            params.put("notifyUrl", payment.getNotifyUrl() + merchant.getId());
            params.put("userIp", req.getIp());
            params.put("userNo", req.getUserId());
            params.put("userName", req.getFromCardUserName());
            params.put("level", "1");
            if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
                if (req.getClientType() == ProjectConstant.ClientType.PC) {
                    params.put("termType","20")
                } else {
                    params.put("termType","10")
                }
                params.put("isQrCode","100")
            }

            String toSign = MD5.toAscii(params) +  payment.getPrivateKey()

            JSONObject headJSON = getHeadJSON(merchant.getMerchantCode(), toSign, req.getCreateDate(),txCode, req.getOrderId())
            JSONObject requestJSON = JSONObject.parseObject(JSON.toJSONString(params))

            Map<String, String> header = new HashMap<>()
            header.put("customerNo", merchantCode)

            paramsJSON.put("head",headJSON)
            paramsJSON.put("request",requestJSON)

            log.info("JinScript_Prepare_Params:{}", JSON.toJSONString(paramsJSON))
            GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
            String restr = okHttpUtil.postJSON(payment.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(paramsJSON), header, requestHeader)
            log.info("JinScript_Prepare_resStr:{}", restr)
            if (StringUtils.isEmpty(restr)) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            restr = restr.substring(1,restr.length()-1);
            restr = restr.trim().replaceAll("\\\\", "")
            JSONObject json = JSON.parseObject(restr);
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            JSONObject dataJSON = json.getJSONObject("response")
            JSONObject headResJSON = json.getJSONObject("head")

            if (dataJSON == null || !dataJSON.getString("returnCode").equals("000000")) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(headResJSON.getString("retMsg"))
                return
            }
            //支付宝转帐跳转
            if (merchant.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER) {
                if (StringUtils.isNotEmpty(dataJSON.getString("payInfo"))) {
                    result.setRedirectUrl(dataJSON.getString("payInfo"))
                    return
                } else {
                    result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                    result.setErrorMsg(headResJSON.getString("retMsg"))
                    return
                }
            }

            String recAcctName = dataJSON.getString("recAcctName")
            String recAcctNo = dataJSON.getString("recAcctNo")
            String bankName = dataJSON.getString("recBankName")
            if (StringUtils.isEmpty(recAcctName) || StringUtils.isEmpty(recAcctNo)
                    || StringUtils.isEmpty(bankName)) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(headResJSON.getString("retMsg"))
                return
            }

            BankInfo bankInfo = new BankInfo();
            bankInfo.setName(recAcctName)
            bankInfo.setBankId(-1)
            bankInfo.setBankName(bankName)
            bankInfo.setCardNo(recAcctNo)
            result.setBankInfo(bankInfo)
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
            JSONObject dataJSON = json.getJSONObject("request")
            String orderId = dataJSON.getString("cusOrderNo")
            if (StringUtils.isNotEmpty(orderId)) {
                return payQuery(okHttpUtil, payment, orderId, args[4])
            }
            return null

        }


        public RechargeNotify payQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
            String orderId = args[2] as String
            this.rechargeBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlRechargeBusiness) as GlRechargeBusiness
            GlRecharge glRecharge = rechargeBusiness.findById(orderId)

            String merchantCode = account.getMerchantCode();
            String tradeDate = DateUtils.format(glRecharge.getCreateDate(), DateUtils.YYYY_MM_DD)

            Map<String, String> params = new HashMap<>()
            params.put("customerNo", merchantCode);
            params.put("cusOrderNo", orderId);
            params.put("tradeDate", tradeDate);
            String toSign = MD5.toAscii(params) +  account.getPrivateKey()

            JSONObject headJSON = getHeadJSON(merchantCode, toSign, glRecharge.getCreateDate(),"P30010", orderId)
            JSONObject requestJSON = JSONObject.parseObject(JSON.toJSONString(params))

            //请求头部
            Map<String, String> header = new HashMap<>()
            header.put("customerNo", merchantCode)

            JSONObject paramsJSON = new JSONObject();
            paramsJSON.put("head",headJSON)
            paramsJSON.put("request",requestJSON)

            log.info("JinScript_Query_reqMap:{}", JSON.toJSONString(paramsJSON))
            GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/pfront/pay/process", JSON.toJSONString(paramsJSON), header, requestHeader)
            log.info("JinScript_Query_resStr:{}", resStr)
            resStr = resStr.substring(1,resStr.length()-1);
            resStr = resStr.trim().replaceAll("\\\\", "")

            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                return null
            }

            JSONObject dataJSON = json.getJSONObject("response")
            JSONObject headRspJSON = json.getJSONObject("head")
            //10-已受理，100-交易成功，-100-交易失败
            if (dataJSON != null && headRspJSON != null && headRspJSON.getString("retCode") == "000000" && dataJSON.getString("status").equals("100")) {
                RechargeNotify pay = new RechargeNotify()
                pay.setAmount(dataJSON.getBigDecimal("payAmt").divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.UP))
                pay.setFee(BigDecimal.ZERO)
                pay.setOrderId(orderId)
                pay.setThirdOrderId(dataJSON.getString("orderNo"))
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


        /**
         * 是否为内部渠道
         *
         * @param args
         * @return
         */
        public boolean innerpay(Object[] args) {
            Integer paymentId = args[1] as Integer
            if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
                return true
            }
            return false
        }

        /**
         * 根据支付方式判断-转帐是否需要实名
         *
         * @param args
         * @return
         */
        public boolean needName(Object[] args) {
            Integer paymentId = args[1] as Integer
            if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                    || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                    || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
                return true
            }
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

        private JSONObject getHeadJSON(String merchantCode, String toSign, Date createDate, String trxCode, String  orderId) {
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