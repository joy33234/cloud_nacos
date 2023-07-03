    package com.seektop.fund.payment.pinganv2pay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
    import com.seektop.common.utils.DateUtils
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
    import java.security.MessageDigest
    import java.security.NoSuchAlgorithmException

    /**
 * 平安支付 -v2
 * @auth joy
 * @date 2021-05-18
 */

    public class PingAnV2Script_withdraw {
        private static final Logger log = LoggerFactory.getLogger(PingAnV2Script_withdraw.class)

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
            DataContentParms.put("batchAmount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParms.put("batchBiztype", "00000")
            DataContentParms.put("batchCount", "1")
            DataContentParms.put("batchDate", DateUtils.format(new Date(), DateUtils.YYYYMMDD))
            DataContentParms.put("batchNo", req.getOrderId())
            DataContentParms.put("batchVersion", "00")
            DataContentParms.put("charset", "UTF-8")
            DataContentParms.put("merchantId", account.getMerchantCode())
            DataContentParms.put("merchantAlias", "bbml")

            StringBuilder batchContent = new StringBuilder();
            batchContent.append("1,")
            batchContent.append(req.getCardNo() + ",")
            batchContent.append(req.getName() + ",")
            batchContent.append(glPaymentChannelBankBusiness.getBankName(req.getBankId(),account.getChannelId()) + ",")
            batchContent.append("上海分行,")
            batchContent.append("上海支行,")
            batchContent.append("0,")
            batchContent.append(req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString() + ",")
            batchContent.append("CNY,")
            batchContent.append("上海市,")
            batchContent.append("上海市,,,,,")
            batchContent.append(req.getOrderId()+",withdraw")
            DataContentParms.put("batchContent", batchContent.toString())


            String sign = UtilSign.GetSHAstr(DataContentParms, account.getPrivateKey());
            DataContentParms.put("sign", "sign")
            DataContentParms.put("signType", "SHA")


            log.info("PingAnV2Script_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String url = account.getPayUrl() + "/agentPay/v1/batch/" + account.getMerchantCode() + DataContentParms.get("batchNo");
            String resStr = okHttpUtil.post(url, DataContentParms, requestHeader)
            log.info("PingAnV2Script_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("respCode") != "200") {
                result.setValid(false)
                result.setMessage(json.getString("respMessage"))
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
            log.info("PingAnV2Script_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

            String orderId = resMap.get("MerchantUniqueOrderId")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("batchDate", DateUtils.format(new Date(), DateUtils.YYYYMMDD))
            DataContentParms.put("batchNo", orderId)
            DataContentParms.put("batchVersion", "00")
            DataContentParms.put("charset", "UTF-8")
            DataContentParms.put("merchantId", merchant.getMerchantCode())

            String sign = UtilSign.GetSHAstr(DataContentParms, merchant.getPrivateKey());
            DataContentParms.put("sign", sign)
            DataContentParms.put("signType", "SHA")

            log.info("PingAnV2Script_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String url = merchant.getPayUrl() + "/agentPay/v1/batch/" + merchant.getMerchantCode() + DataContentParms.get("batchNo");
            String resStr = okHttpUtil.get(url, DataContentParms, requestHeader)
            log.info("PingAnV2Script_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getString("respCode") != "200") {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId.toUpperCase())
            //订单状态：反馈状态，null(处理中)，成功，失败
            if (json.getString("tradeFeedbackcode") == "成功") {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")
            } else if (json.getString("tradeFeedbackcode") == "失败") {
                notify.setStatus(1)
                notify.setRsp("SUCCESS")
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("customerNo", merchantAccount.getMerchantCode())

            String sign = UtilSign.GetSHAstr(DataContentParms, merchantAccount.getPrivateKey());
            DataContentParms.put("sign", sign)
            DataContentParms.put("signType", "SHA")

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("PingAnV2Script_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParms))
            String resStr = okHttpUtil.get(merchantAccount.getPayUrl() + "/search/queryBalance", DataContentParms, requestHeader)
            log.info("PingAnV2Script_QueryBalance_resStr: {}", resStr)

            if (ObjectUtils.isEmpty(resStr)) {
                return BigDecimal.ZERO;
            }
            try {
                int statusStart = resStr.indexOf("<status>")
                int statusEnd = resStr.indexOf("</status>")
                String status = resStr.substring(statusStart + 8, statusEnd);
                if ( status != "succ") {
                    return BigDecimal.ZERO;
                }
                int reasonStart = resStr.indexOf("<reason>")
                int reasonEnd = resStr.indexOf("</reason>")
                String reason = resStr.substring(reasonStart + 8, reasonEnd);
                if (StringUtils.isEmpty(reason)) {
                    return BigDecimal.ZERO;
                }
                return new BigDecimal(reason)
            } catch(Exception e) {
               log.error("PingAnV2Script_QueryBalance_resStr_error", e.getMessage())
            }
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


    class UtilSign{

        public static String GetSHAstr(Map<String,String> Parm,String Key){
            if(Parm.containsKey("sign")){
                Parm.remove("sign");//������sign
            }
            List<String> SortStr = Ksort(Parm); //����
            String Md5Str = CreateLinkstring(Parm,SortStr);
            return SHAUtils.sign(Md5Str+Key, "utf-8");
        }

        /**
         * ����  (����)
         * @param Parm
         * @return
         */
        public static List<String> Ksort(Map<String,String> Parm){
            List<String> SMapKeyList = new ArrayList<String>(Parm.keySet());
            Collections.sort(SMapKeyList);
            return SMapKeyList;
        }

        /**
         * �ж�ֵ�Ƿ�Ϊ�� FALSE Ϊ����  TRUE Ϊ��
         * @param Temp
         * @return
         */
        public static boolean StrEmpty(String Temp){
            if(null == Temp || Temp.isEmpty()){
                return true;
            }
            return false;
        }

        /**
         * ƴ�ӱ���
         * @param Parm
         * @param SortStr
         * @return
         */
        public static String CreateLinkstring(Map<String,String> Parm,List<String> SortStr){
            String LinkStr = "";
            for(int i=0;i<SortStr.size();i++){
                if(!StrEmpty(Parm.get(SortStr.get(i).toString()))){
                    LinkStr += SortStr.get(i) +"="+Parm.get(SortStr.get(i).toString());
                    if((i+1)<SortStr.size()){
                        LinkStr +="&";
                    }
                }
            }
            return LinkStr;
        }
    }


    class SHAUtils {

        public static final String SIGN_ALGORITHMS = "SHA-1";

        /**
         * SHA1 安全加密算法
         */
        public static String sign(String content,String inputCharset)  {
            //获取信息摘要 - 参数字典排序后字符串
            try {
                //指定sha1算法
                MessageDigest digest = MessageDigest.getInstance(SIGN_ALGORITHMS);
                digest.update(content.getBytes(inputCharset));
                //获取字节数组
                byte[] messageDigest = digest.digest();
                // Create Hex String
                StringBuffer hexString = new StringBuffer();
                // 字节数组转换为 十六进制 数
                for (int i = 0; i < messageDigest.length; i++) {
                    String shaHex = Integer.toHexString(messageDigest[i] & 0xFF);
                    if (shaHex.length() < 2) {
                        hexString.append(0);
                    }
                    hexString.append(shaHex);
                }
                return hexString.toString().toUpperCase();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }