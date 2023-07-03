package com.seektop.fund.payment.ttpay;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;


public class SignUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SignUtils.class);
	/** 
     * 除去数组中的空值和签名参数
     * @param sArray 签名参数组
     * @return 去掉空值与签名参数后的新签名参数组
     */
    public static Map<String, String> paraFilter(Map<String, ? extends Object> sArray) {

        Map<String, String> result = new HashMap<String, String>();

        if (sArray == null || sArray.size() <= 0) {
            return result;
        }

        for (String key : sArray.keySet()) {
            Object value = sArray.get(key);
            if (value == null || value.toString().equals("") || key.equalsIgnoreCase("sign")
                || key.equalsIgnoreCase("sign_type") || key.equalsIgnoreCase("signType")) {
                continue;
            }
            result.put(key, value.toString());
        }

        return result;
    }

    /** 
     * 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
     * @param params 需要排序并参与字符拼接的参数组
     * @return 拼接后字符串
     */
    public static String createLinkString(Map<String, String> params) {

        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);

        String prestr = "";

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);

            if (i == keys.size() - 1) {//拼接时，不包括最后一个&字符
                prestr = prestr + key + "=" + value;
            } else {
                prestr = prestr + key + "=" + value + "&";
            }
        }
        return prestr;
    }

    /**
     *
     * @param params
     * @param partnerSignKey
     * @return
     */
    public static final boolean verify(Map<String, String> params, String partnerSignKey, String charset){
        Map<String,String> result = SignUtils.paraFilter(params);  //掉空值与签名参数后的新签名参数组
        String linkStr = SignUtils.createLinkString(result);  //把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
        String partnerSign = params.get("sign");
        linkStr = linkStr + partnerSignKey;
        String ourSign = DigestUtils.md5Hex(getContentBytes(linkStr, charset));
        boolean isEqual =  ourSign.equals(partnerSign);
        if(!isEqual){
            LOG.info("params filter:{}",result);
            LOG.info("params linkStr:{}",linkStr);
            LOG.info("MD5验签partner签名不通过,partnerSign:{},ourSign:{}",partnerSign,ourSign);
        }
        return isEqual;
    }

    public static final boolean verify(Map<String, String> params, String partnerSignKey, String charset, String signType){
        Map<String,String> result = SignUtils.paraFilter(params);  //掉空值与签名参数后的新签名参数组
        String linkStr = SignUtils.createLinkString(result);  //把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
        LOG.info("linkStr:{}", linkStr);
        System.out.println("linkStr:" + linkStr);
        String partnerSign = params.get("sign");

        if(AccConstants.SignType.RSA.equalsIgnoreCase(signType)){
            //公钥验签
            return rsaCheckContent(linkStr,partnerSign,partnerSignKey,charset);
        }else {
            linkStr = linkStr + partnerSignKey;
            String ourSign = DigestUtils.md5Hex(getContentBytes(linkStr, charset));
            boolean isEqual =  ourSign.equals(partnerSign);
            if(!isEqual){
                LOG.info("params filter:{}",result);
                LOG.info("params linkStr:{}",linkStr);
                LOG.info("MD5验签partner签名不通过,partnerSign:{},ourSign:{}",partnerSign,ourSign);
            }
            return isEqual;
        }
    }

    /**
     * @param content
     * @param charset
     * @return
     * @throws SignatureException
     * @throws UnsupportedEncodingException
     */
    private static byte[] getContentBytes(String content, String charset) {
        if (charset == null || "".equals(charset)) {
            return content.getBytes();
        }
        try {
            return content.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("MD5签名过程中出现错误,指定的编码集不对,您目前指定的编码集是:" + charset);
        }
    }

    public static String sign(Map<String, ? extends Object> params, String key, String charset){
        Map<String,String> result = SignUtils.paraFilter(params);
        String text = SignUtils.createLinkString(result); //把数组所有元素，按照“参数=参数值”的模式用“&”字符拼接成字符串
        text = text + key;
        return DigestUtils.md5Hex(getContentBytes(text, charset));
    }

    public static String sign(Map<String, ? extends Object> params, String key, String charset, String signType){
        Map<String,String> result = SignUtils.paraFilter(params);
        String text = SignUtils.createLinkString(result); //把数组所有元素，按照“参数=参数值”的模式用“&”字符拼接成字符串
        if(AccConstants.SignType.RSA.equalsIgnoreCase(signType)){
            return rsaSign(text,key,charset);
        }else {
            text = text + key;
            return DigestUtils.md5Hex(getContentBytes(text, charset));
        }
    }

    public static String rsaSign(String content, String privateKey, String charset) {
        try {
            PrivateKey priKey = getPrivateKeyFromPKCS8("RSA", new ByteArrayInputStream(privateKey.getBytes()));
            Signature signature = Signature.getInstance("SHA1WithRSA");
            signature.initSign(priKey);
            if (StringUtils.isEmpty(charset)) {
                signature.update(content.getBytes());
            } else {
                signature.update(content.getBytes(charset));
            }

            byte[] signed = signature.sign();
            return new String(Base64.encodeBase64(signed));
        } catch (InvalidKeySpecException var6) {
            throw new RuntimeException("RSA私钥格式不正确，请检查是否正确配置了PKCS8格式的私钥", var6);
        } catch (Exception var7) {
            throw new RuntimeException("RSAcontent = " + content + "; charset = " + charset, var7);
        }
    }

    public static boolean rsaCheckContent(String content, String sign, String publicKey, String charset){
        try {
            PublicKey pubKey = getPublicKeyFromX509("RSA", new ByteArrayInputStream(publicKey.getBytes()));
            Signature signature = Signature.getInstance("SHA1WithRSA");
            signature.initVerify(pubKey);
            if (StringUtils.isEmpty(charset)) {
                signature.update(content.getBytes());
            } else {
                signature.update(content.getBytes(charset));
            }

            return signature.verify(Base64.decodeBase64(sign.getBytes()));
        } catch (Exception var6) {
            throw new RuntimeException("RSAcontent = " + content + ",sign=" + sign + ",charset = " + charset, var6);
        }
    }

    public static PublicKey getPublicKeyFromX509(String algorithm, InputStream ins) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        StringWriter writer = new StringWriter();
        StreamUtil.io(new InputStreamReader(ins), writer);
        byte[] encodedKey = writer.toString().getBytes();
        encodedKey = Base64.decodeBase64(encodedKey);
        return keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
    }

    public static PrivateKey getPrivateKeyFromPKCS8(String algorithm, InputStream ins) throws Exception {
        if (ins != null && !StringUtils.isEmpty(algorithm)) {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            byte[] encodedKey = StreamUtil.readText(ins).getBytes();
            encodedKey = Base64.decodeBase64(encodedKey);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
        } else {
            return null;
        }
    }
}
