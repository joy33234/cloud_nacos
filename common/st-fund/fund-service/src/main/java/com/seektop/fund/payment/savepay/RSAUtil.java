package com.seektop.fund.payment.savepay;

import lombok.extern.slf4j.Slf4j;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class RSAUtil {


    public static final String KEY_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /**
     * 用私钥对信息生成数字签名
     *
     * @param privateKey 私钥
     * @return
     * @throws Exception
     */
    public static String sign(TreeMap<String, String> treeMap, PrivateKey privateKey) throws Exception {

        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append("&" + key.trim() + "=" + value.trim());
        }

        String strMap = sb.toString().substring(1);
        log.info("toSign:{}", strMap);
        byte[] data = strMap.getBytes(Charset.forName("utf-8"));
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 用公钥加密
     *
     * @param data 明文
     *             //     * @param PublicKey 公钥
     * @return
     * @throws Exception
     */
    public static String encryptByPublicKey(byte[] data, PublicKey publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(data));
    }

    /**
     * 校验数字签名
     *
     * @param publicKey 公钥
     * @param sign      数字签名
     * @return 校验成功返回true 失败返回false
     * @throws Exception
     */
    public static boolean verify(TreeMap<String, String> treeMap, PublicKey publicKey, String sign) throws Exception {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append("&" + key.trim() + "=" + value.trim());
        }

        String strMap = sb.toString().substring(1);
        byte[] data = strMap.getBytes(Charset.forName("utf-8"));
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(decryptBASE64(sign));
    }

    public static byte[] decryptBASE64(String key) throws Exception {
        return (Base64.getDecoder()).decode(key);
    }

    public static PublicKey getPublicKey(String key) throws Exception {

        Base64.Decoder decoder = Base64.getDecoder();
        byte[] b = decoder.decode(key);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(b);
        PublicKey pubKey = kf.generatePublic(keySpec);
        return pubKey;
    }

    public static PrivateKey getPrivateKey(String key) throws Exception {

        DerInputStream derReader = new DerInputStream(Base64.getDecoder().decode(key));
        DerValue[] seq = derReader.getSequence(0);
        // skip version seq[0];
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec =
                new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }

}
