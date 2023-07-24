package com.seektop.fund.payment.lefupay;

import com.alibaba.fastjson.JSONObject;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.util.Base64Utils;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * RSA公钥/私钥/签名工具包
 * 字符串格式的密钥在未在特殊说明情况下都为BASE64编码格式<br/>
 * 由于非对称加密速度极其缓慢，一般文件不使用它来加密而是使用对称加密，<br/>
 * 非对称加密算法可以用来对对称加密的密钥加密，这样保证密钥的安全也就保证了数据的安全
 *
 * @author 码农猿
 */
public class RSAUtils {
    /**
     * 加密算法RSA
     */
    private static final String KEY_ALGORITHM = "RSA";
    /**
     * 获取公钥的key
     */
    private static final String PUBLIC_KEY = "PUBLIC_KEY";
    /**
     * 获取私钥的keys
     */
    private static final String PRIVATE_KEY = "PRIVATE_KEY";
    /**
     * 签名算法
     */
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
    /**
     * 常量0
     */
    private static final int ZERO = 0;
    /**
     * RSA最大加密明文最大大小
     */
    private static final int MAX_ENCRYPT_BLOCK = 117;
    /**
     * RSA最大解密密文最大大小
     * 当密钥位数为1024时,解密密文最大是 128
     * 当密钥位数为2048时需要改为 256 不然会报错（Decryption error）
     */
    private static final int MAX_DECRYPT_BLOCK = 128;
    /**
     * 默认key大小
     */
    private static final int DEFAULT_KEY_SIZE = 1024;

    /**
     * 生成密钥对(公钥和私钥)
     */
    public static Map<String, Object> initKey() throws Exception {
        return initKey(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成密钥对(公钥和私钥)
     */
    public static Map<String, Object> initKey(int keySize) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGen.initialize(keySize);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        Map<String, Object> keyMap = new HashMap<>(2);
        keyMap.put(PUBLIC_KEY, publicKey);
        keyMap.put(PRIVATE_KEY, privateKey);
        return keyMap;
    }


    /**
     * 公钥加密
     *
     * @param data      源数据
     * @param publicKey 公钥(BASE64编码)
     */
    public static byte[] encryptByPublicKey(byte[] data, String publicKey)
            throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return encrypt(data, KeyFactory.getInstance(KEY_ALGORITHM), keyFactory.generatePublic(x509KeySpec));
    }


    /**
     * 私钥加密
     *
     * @param data       源数据
     * @param privateKey 私钥(BASE64编码)
     */
    public static byte[] encryptByPrivateKey(byte[] data, String privateKey)
            throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        return encrypt(data, keyFactory, privateK);
    }


    /**
     * 私钥解密
     *
     * @param encryptedData 已加密数据
     * @param privateKey    私钥(BASE64编码)
     */
    public static byte[] decryptByPrivateKey(byte[] encryptedData, String privateKey)
            throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return decrypt(encryptedData, keyFactory, keyFactory.generatePrivate(pkcs8KeySpec));
    }


    /**
     * 公钥解密
     *
     * @param encryptedData 已加密数据
     * @param publicKey     公钥(BASE64编码)
     */
    public static byte[] decryptByPublicKey(byte[] encryptedData, String publicKey)
            throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);
        return decrypt(encryptedData, keyFactory, publicK);

    }


    /**
     * 用私钥对信息生成数字签名
     */
    public static String sign(byte[] data, String privateKey) throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateK);
        Arrays.sort(data);
        signature.update(data);
        return Base64Utils.encodeToString(signature.sign());
    }


    /**
     * 校验数字签名
     */
    public static boolean verify(byte[] data, String publicKey, String sign)
            throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        PublicKey publicK = keyFactory.generatePublic(keySpec);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicK);
        Arrays.sort(data);
        signature.update(data);
        return signature.verify(Base64Utils.decodeFromString(sign));
    }


    /**
     * 获取私钥
     */
    public static String getPrivateKey(Map<String, Object> keyMap) {
        Key key = (Key) keyMap.get(PRIVATE_KEY);
        return Base64Utils.encodeToString(key.getEncoded());
    }


    /**
     * 获取公钥
     */
    public static String getPublicKey(Map<String, Object> keyMap) {
        Key key = (Key) keyMap.get(PUBLIC_KEY);
        return Base64Utils.encodeToString(key.getEncoded());
    }

    /**
     * 解密公共方法
     */
    private static byte[] decrypt(byte[] data, KeyFactory keyFactory, Key key) throws Exception {

        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, key);
        return encryptAndDecrypt(data, cipher, MAX_DECRYPT_BLOCK);
    }

    /**
     * 加密公共方法
     */
    private static byte[] encrypt(byte[] data, KeyFactory keyFactory, Key key) throws Exception {
        // 对数据加密
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return encryptAndDecrypt(data, cipher, MAX_ENCRYPT_BLOCK);
    }


    /**
     * 加密解密分段处理公共方法
     */
    private static byte[] encryptAndDecrypt(byte[] data, Cipher cipher, int maxSize) throws Exception {
        int inputLen = data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = ZERO;
        byte[] cache;
        int i = ZERO;
        // 对数据分段加密
        while (inputLen - offSet > ZERO) {
            if (inputLen - offSet > maxSize) {
                cache = cipher.doFinal(data, offSet, maxSize);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen - offSet);
            }
            out.write(cache, ZERO, cache.length);
            i++;
            offSet = i * maxSize;
        }
        byte[] encryptedData = out.toByteArray();
        out.close();
        return encryptedData;
    }

    public static void main(String[] args) throws Exception {
        JSONObject data = new JSONObject();
        data.put("merchant_code", "xxxx12321");
        data.put("merchant_amount", 199.00);
        data.put("merchant_order_no", "xxxxxxxxxx");
        //生成公钥与私钥
        Map<String, Object> initKeyMap = RSAUtils.initKey(1024);
        //公钥
        String publicKey = RSAUtils.getPublicKey(initKeyMap);
        System.out.println("公钥：" + publicKey);
        //私钥
        String privateKey = RSAUtils.getPrivateKey(initKeyMap);
        System.out.println("私钥：" + privateKey);
        String msg = "msg,abc,+-:,测试";
        String encrypted = Base64.encodeBase64String(RSAUtils.encryptByPublicKey(data.toJSONString().getBytes(StandardCharsets.UTF_8), publicKey));
        System.out.println("加密后：" + encrypted);
        String decrypted = new String(RSAUtils.decryptByPrivateKey(Base64.decodeBase64("P3Apc0jsyxBqgah6seh2ymONT+DhHxX5W6YUf7/QvCFPc6wbOSLcJAVvjkmqKofE1ZZycSjdnwOTZWP3D8WoOi3ry3d8jWa536NZwCQ9MdWNreZNitdXWe4nbOJdf3tdhYkeSviGkyGpzH2xL6wOpgqCytSawuLcSX9tj9ZaBwo=".getBytes()), privateKey));
        System.out.println("解密后：" + decrypted);
    }
}
