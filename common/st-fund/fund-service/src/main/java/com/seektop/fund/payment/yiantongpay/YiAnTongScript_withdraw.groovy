    package com.seektop.fund.payment.yiantongpay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
    import com.seektop.common.utils.DateUtils
    import com.seektop.common.utils.MD5
    import com.seektop.enumerate.GlActionEnum
    import com.seektop.exception.GlobalException
    import com.seektop.fund.business.GlPaymentChannelBankBusiness
    import com.seektop.fund.model.GlWithdraw
    import com.seektop.fund.model.GlWithdrawMerchantAccount
    import com.seektop.fund.payment.WithdrawNotify
    import com.seektop.fund.payment.WithdrawResult
    import com.seektop.fund.payment.groovy.ResourceEnum
    import org.apache.commons.codec.binary.Base64
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.util.Base64Utils
    import org.springframework.util.ObjectUtils
    import sun.misc.BASE64Decoder
    import sun.misc.BASE64Encoder

    import javax.crypto.BadPaddingException
    import javax.crypto.Cipher
    import javax.crypto.IllegalBlockSizeException
    import javax.crypto.NoSuchPaddingException
    import java.math.RoundingMode
    import java.security.*
    import java.security.interfaces.RSAPrivateKey
    import java.security.interfaces.RSAPublicKey
    import java.security.spec.PKCS8EncodedKeySpec
    import java.security.spec.X509EncodedKeySpec

    /**
 * 易安通支付
 * @auth joy
 * @date 2021-05-30
 */

    public class YiAnTongScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(YiAnTongScript_withdraw.class)

        private OkHttpUtil okHttpUtil


        private RedisService redisService

        private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


        WithdrawResult withdraw(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
            GlWithdraw req = args[2] as GlWithdraw
            this.redisService = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService
            this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

            Date now = new Date();
            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("mermberId", account.getMerchantCode())
            DataContentParams.put("version", "1.1.0")
            DataContentParams.put("productId", "901118")
            DataContentParams.put("transId", req.getOrderId())
            DataContentParams.put("transTime", DateUtils.format(now, DateUtils.YYYY_MM_DD_HH_MM_SS))
            DataContentParams.put("name", req.getName())
            DataContentParams.put("idCardNo", "450902199611092917")
            DataContentParams.put("phoneNo", "13387755197")
            DataContentParams.put("cardType", "101")
            DataContentParams.put("loanBankCardNo", req.getCardNo())
            DataContentParams.put("loanUse", "日常消费")
            DataContentParams.put("productName", "易安")
            DataContentParams.put("loanAmount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParams.put("instalmentPeriods", "1")
            DataContentParams.put("instalmentTermDay", "7")
            DataContentParams.put("beginDate", DateUtils.format(now, DateUtils.YYYY_MM_DD))
            DataContentParams.put("endDate", DateUtils.format(DateUtils.addDay(7,now), DateUtils.YYYY_MM_DD))
            DataContentParams.put("productInterestType", "00")
            DataContentParams.put("productPunishType", "00")
            DataContentParams.put("productPunishRate", "0")
            DataContentParams.put("repayBankCardNo", req.getCardNo())
            DataContentParams.put("repayName", req.getName())
            DataContentParams.put("reservePhoneNo", "13387755197")
            DataContentParams.put("returnUrl", account.getNotifyUrl() + account.getMerchantId())
            DataContentParams.put("loanDays", "7")

            JSONObject params = new JSONObject();
            params.put("orgCode",account.getMerchantCode())
            params.put("serviceId","creditLoan")
            params.put("data", RSA.encryptedDataOnJava(JSON.toJSONString(DataContentParams), account.getPublicKey()))
            params.put("signature", MD5.md5(MD5.toAscii(DataContentParams)))

            log.info("YiAnTongScript_Transfer_params: {}", JSON.toJSONString(DataContentParams))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.postJSON(account.getPayUrl() + "/open-web/wimiftService.json", JSON.toJSONString(params), requestHeader)
            log.info("YiAnTongScript_Transfer_resStr: {}", resStr)
            String decryptData = RSAUtils.decryptByBlock(account.getPrivateKey(), resStr);
            log.info("YiAnTongScript_Transfer_decryptData: {}", decryptData)

            JSONObject json = JSON.parseObject(decryptData)

            WithdrawResult result = new WithdrawResult()
            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParams))
            result.setResData(decryptData)

            if (ObjectUtils.isEmpty(json)) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("resultCode") != "1") {
                result.setValid(false)
                result.setMessage(json.getString("errorDesc"))
                return result
            }
            JSONObject dataJSON = json.getJSONObject("data");
            if (ObjectUtils.isEmpty(dataJSON) || StringUtils.isEmpty(dataJSON.getString("orderNo"))) {
                result.setValid(false)
                result.setMessage(json.getString("errorDesc"))
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
            log.info("YiAnTongScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            String dataStr = JSON.parseObject(resMap.get("reqBody")).getString("data")
            String decryptData = RSAUtils.decryptByBlock(merchant.getPrivateKey(), dataStr);
            JSONObject jsonObject = JSONObject.parseObject(decryptData)
            log.info("YiAnTongScript_withdrawNotify_decryptData:{}",decryptData)
            String orderId = jsonObject.getString("loanTransId")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("mermberId", merchant.getMerchantCode())
            DataContentParams.put("version", "1.1.0")
            DataContentParams.put("loanTransId", orderId)

            JSONObject params = new JSONObject();
            params.put("orgCode",merchant.getMerchantCode())
            params.put("serviceId","creditLoanResult")
            params.put("data", RSA.encryptedDataOnJava(JSON.toJSONString(DataContentParams), merchant.getPublicKey()))
            params.put("signature", MD5.md5(MD5.toAscii(DataContentParams)))

            log.info("YiAnTongScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParams))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.postJSON(merchant.getPayUrl() + "/open-web/wimiftService.json", JSON.toJSONString(params), requestHeader)
            log.info("YiAnTongScript_TransferQuery_resStr:{}", resStr)
            String decryptData = RSAUtils.decryptByBlock(merchant.getPrivateKey(), resStr);
            log.info("YiAnTongScript_TransferQuery_decryptData: {}", decryptData)

            JSONObject json = JSON.parseObject(decryptData)
            if (ObjectUtils.isEmpty(json) || json.getString("resultCode") != "1") {
                return null
            }

            JSONObject dataJSON = json.getJSONObject("data")
            if (ObjectUtils.isEmpty(dataJSON)) {
                return null
            }

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId(dataJSON.getString("platOrderNo"))
            //订单状态判断标准：0:借款失败 1:借款成 功 2:借款未知 3:已结清 4:结清处理中
            if (dataJSON.getString("loanResult") == "1") {
                notify.setStatus(0)
                notify.setRsp("200")
            } else if (dataJSON.getString("loanResult") == "0") {
                notify.setStatus(1)
                notify.setRsp("200")
            } else {
                notify.setStatus(2)
            }
            log.info(JSON.toJSONString(notify))
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("mermberId", merchantAccount.getMerchantCode())
            DataContentParams.put("version", "1.1.0")
            DataContentParams.put("transId", System.currentTimeMillis().toString())

            JSONObject params = new JSONObject();
            params.put("orgCode",merchantAccount.getMerchantCode())
            params.put("serviceId","accountBalance")
            params.put("data", RSA.encryptedDataOnJava(JSON.toJSONString(DataContentParams), merchantAccount.getPublicKey()))
            params.put("signature", MD5.md5(MD5.toAscii(DataContentParams)))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("YiAnTongScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
            String resStr = okHttpUtil.postJSONLimitTime(merchantAccount.getPayUrl() + "/open-web/wimiftService.json", JSON.toJSONString(params), requestHeader,60 )
            log.info("YiAnTongScript_QueryBalance_resStr: {}", resStr)
            if (ObjectUtils.isEmpty(resStr)){
                return BigDecimal.ZERO;
            }
            String decryptData = RSAUtils.decryptByBlock(merchantAccount.getPrivateKey(), resStr);
            log.info("YiAnTongScript_QueryBalance_decryptData: {}", decryptData)
            JSONObject responJSON = JSON.parseObject(decryptData)
            if (ObjectUtils.isEmpty(responJSON) || responJSON.getString("resultCode") != "1") {
                return BigDecimal.ZERO;
            }
            JSONObject dataJSON = responJSON.getJSONObject("data");
            if (dataJSON != null) {
                return dataJSON.getBigDecimal("totalBal") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("totalBal")
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

    class RSA {
        public static final String KEY_ALGORITHM = "RSA";
        public static final String SIGNATURE_ALGORITHM = "MD5withRSA";
        private static final String PUBLIC_KEY = "RSAPublicKey";
        private static final String PRIVATE_KEY = "RSAPrivateKey";
        private static final int MAX_ENCRYPT_BLOCK = 117;
        private static final int MAX_DECRYPT_BLOCK = 128;

        public RSA() {
        }

        public static byte[] decryptBase64(String key) throws Exception {
            return (new BASE64Decoder()).decodeBuffer(key);
        }

        public static String encryptBase64(byte[] key) throws Exception {
            return (new BASE64Encoder()).encodeBuffer(key);
        }

        public static Map<String, Object> genKeyPair() throws Exception {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(1024);
            KeyPair keyPair = keyPairGen.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey)keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey)keyPair.getPrivate();
            Map<String, Object> keyMap = new HashMap(2);
            keyMap.put("RSAPublicKey", publicKey);
            keyMap.put("RSAPrivateKey", privateKey);
            return keyMap;
        }

        public static String sign(byte[] data, String privateKey) throws Exception {
            byte[] keyBytes = decryptBase64(privateKey);
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Signature signature = Signature.getInstance("MD5withRSA");
            signature.initSign(privateK);
            signature.update(data);
            return encryptBase64(signature.sign());
        }

        public static boolean verify(byte[] data, String publicKey, String sign) throws Exception {
            byte[] keyBytes = decryptBase64(publicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicK = keyFactory.generatePublic(keySpec);
            Signature signature = Signature.getInstance("MD5withRSA");
            signature.initVerify(publicK);
            signature.update(data);
            return signature.verify(decryptBase64(sign));
        }

        public static byte[] decryptByPrivateKey(byte[] encryptedData, String privateKey) throws Exception {
            byte[] keyBytes = decryptBase64(privateKey);
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(2, privateK);
            int inputLen = encryptedData.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offSet = 0;

            for(int i = 0; inputLen - offSet > 0; offSet = i * 128) {
                byte[] cache;
                if (inputLen - offSet > 128) {
                    cache = cipher.doFinal(encryptedData, offSet, 128);
                } else {
                    cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
                }

                out.write(cache, 0, cache.length);
                ++i;
            }

            byte[] decryptedData = out.toByteArray();
            out.close();
            return decryptedData;
        }

        public static byte[] encryptByPublicKey(byte[] data, String publicKey) throws Exception {
            byte[] keyBytes = decryptBase64(publicKey);
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Key publicK = keyFactory.generatePublic(x509KeySpec);
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(1, publicK);
            int inputLen = data.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offSet = 0;

            for(int i = 0; inputLen - offSet > 0; offSet = i * 117) {
                byte[] cache;
                if (inputLen - offSet > 117) {
                    cache = cipher.doFinal(data, offSet, 117);
                } else {
                    cache = cipher.doFinal(data, offSet, inputLen - offSet);
                }

                out.write(cache, 0, cache.length);
                ++i;
            }

            byte[] encryptedData = out.toByteArray();
            out.close();
            return encryptedData;
        }


        public static String getPrivateKey(Map<String, Object> keyMap) throws Exception {
            Key key = (Key)keyMap.get("RSAPrivateKey");
            return encryptBase64(key.getEncoded());
        }

        public static String getPublicKey(Map<String, Object> keyMap) throws Exception {
            Key key = (Key)keyMap.get("RSAPublicKey");
            return encryptBase64(key.getEncoded());
        }

        public static String encryptedDataOnJava(String data, String publicKey) throws Exception {
            return encryptBase64(encryptByPublicKey(data.getBytes("UTF-8"), publicKey));
        }

        public static String decryptDataOnJava(String data, String privateKey) throws Exception {
            byte[] rs = decryptBase64(data);
            return new String(decryptByPrivateKey(rs, privateKey), "UTF-8");
        }
    }


    class RSAUtils {

        /**
         * 加密算法RSA
         */
        public static final String KEY_ALGORITHM = "RSA";

        /**
         * 签名算法
         */
        public static final String SIGNATURE_ALGORITHM = "MD5withRSA";

        public static final Integer BLOCK_LENGTH = 128;

        public static final Integer DECRYPT_BLOCK_LENGTH = 117;

        /**
         * <p>
         * 用私钥对信息生成数字签名
         * </p>
         *
         * @param data
         *            已加密数据
         * @param privateKey
         *            私钥(BASE64编码)
         *
         * @return
         * @throws Exception
         */
        public static String sign(byte[] data, String privateKey) throws Exception {

            byte[] keyBytes = Base64Utils.decode(privateKey.getBytes());
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateK);
            signature.update(data);
            return Base64Utils.encode(signature.sign()).toString();
        }

        /**
         * <p>
         * 私钥解密
         * </p>
         *
         * @return
         * @throws Exception
         */
        public static String decrypt(String key, String data) throws Exception {
            byte[] decryptBody = new Base64().decode(data);// 返回body
            byte[] keyBytes = new Base64().decode(key);
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, privateK);
            byte[] decryptedData = cipher.doFinal(decryptBody);
            return new String(decryptedData, "UTF-8");
        }

        /**
         * 私钥分段解密
         */
        public static String decryptByBlock(String key, String data) throws Exception {
            byte[] encryptedData = new Base64().decode(data);// 返回body
            byte[] keyBytes = new Base64().decode(key);

            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());// keyFactory.getAlgorithm()
            cipher.init(Cipher.DECRYPT_MODE, privateK);
            int inputLen = encryptedData.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offSet = 0;

            byte[] decryptedData;
            int i = 0;
            // 对数据分段解密
            if (inputLen > BLOCK_LENGTH) {
                byte[] cache;
                while (inputLen - offSet > 0) {
                    if (inputLen - offSet > BLOCK_LENGTH) {
                        cache = cipher.doFinal(encryptedData, offSet, BLOCK_LENGTH);
                    } else {
                        cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
                    }
                    out.write(cache, 0, cache.length);
                    i++;
                    offSet = i * BLOCK_LENGTH;
                }
                decryptedData = out.toByteArray();
            } else {
                decryptedData = cipher.doFinal(encryptedData);
            }

            out.close();
            return new String(decryptedData, "UTF-8");
        }

        /**
         * 公钥加密过程
         *
         *            公钥
         * @param plainTextData
         *            明文数据
         * @return
         * @throws Exception
         *             加密过程中的异常信息
         */
        public static String encrypt(String key, byte[] plainTextData) throws Exception {
            byte[] keyByte = new Base64().decode(key.getBytes());
            Cipher cipher = null;
            try {
                X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyByte);
                KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
                Key publicK = keyFactory.generatePublic(x509KeySpec);
                // 使用默认RSA
                cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, publicK);
                byte[] output = cipher.doFinal(plainTextData);
                return new Base64().encodeToString(output);
            } catch (NoSuchAlgorithmException e) {
                throw new Exception("无此加密算法");
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
                return null;
            } catch (InvalidKeyException e) {
                throw new Exception("加密公钥非法,请检查");
            } catch (IllegalBlockSizeException e) {
                throw new Exception("明文长度非法");
            } catch (BadPaddingException e) {
                throw new Exception("明文数据已损坏");
            }
        }

        /**
         * 公钥加密过程
         *
         *            公钥
         * @param plainTextData
         *            明文数据
         * @return
         * @throws Exception
         *             加密过程中的异常信息
         */
        public static String encryptByBlock(String key, byte[] plainTextData) throws Exception {
            byte[] keyByte = new Base64().decode(key.getBytes());
            Cipher cipher = null;
            try {
                X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyByte);
                KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
                Key publicK = keyFactory.generatePublic(x509KeySpec);
                // 使用默认RSA
                cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, publicK);

                int inputLen = plainTextData.length;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int offSet = 0;
                byte[] decryptedData;
                int i = 0;
                // 对数据分段解密
                if (inputLen > DECRYPT_BLOCK_LENGTH) {

                    byte[] cache;
                    while (inputLen - offSet > 0) {
                        if (inputLen - offSet > DECRYPT_BLOCK_LENGTH) {
                            cache = cipher.doFinal(plainTextData, offSet, DECRYPT_BLOCK_LENGTH);
                        } else {
                            cache = cipher.doFinal(plainTextData, offSet, inputLen - offSet);
                        }
                        out.write(cache, 0, cache.length);
                        i++;
                        offSet = i * DECRYPT_BLOCK_LENGTH;
                    }
                    decryptedData = out.toByteArray();
                } else {
                    decryptedData = cipher.doFinal(plainTextData);
                }

                out.close();

                return new Base64().encodeToString(decryptedData);
            } catch (NoSuchAlgorithmException e) {
                throw new Exception("无此加密算法");
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
                return null;
            } catch (InvalidKeyException e) {
                throw new Exception("加密公钥非法,请检查");
            } catch (IllegalBlockSizeException e) {
                throw new Exception("明文长度非法");
            } catch (BadPaddingException e) {
                throw new Exception("明文数据已损坏");
            }
        }
    }