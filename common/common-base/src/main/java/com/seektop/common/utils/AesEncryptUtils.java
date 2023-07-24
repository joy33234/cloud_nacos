package com.seektop.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


@UtilityClass
@Slf4j
public class AesEncryptUtils {

    //可配置到Constant中，并读取配置文件注入,16位,自己定义
    private static final String KEY = "seektop_aesgame@";
    //小金专用key
    private static final String XJ_KEY = "seektop_xj@game@";

    //参数分别代表 算法名称/加密模式/数据填充方式
    private static final String ALGORITHMSTR = "AES/ECB/PKCS5Padding";

    /**
     * 加密
     *
     * @param content    加密的字符串
     * @param encryptKey key值
     * @return
     * @throws Exception
     */
    public static String encrypt(String content, String encryptKey) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);
        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptKey.getBytes(), "AES"));
        byte[] b = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
        // 采用base64算法进行转码,避免出现中文乱码
        return Base64.getUrlEncoder().encodeToString(b);

    }
    public static String encryptBase64(String content, String encryptKey) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);
        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptKey.getBytes(), "AES"));
        byte[] b = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
        // 采用base64算法进行转码,避免出现中文乱码
        return Base64.getEncoder().encodeToString(b);

    }

    /**
     * 解密
     *
     * @param encryptStr 解密的字符串
     * @param decryptKey 解密的key值
     * @return
     * @throws Exception
     */
    public static String decrypt(String encryptStr, String decryptKey)
            throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);
        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptKey.getBytes(), "AES"));
        // 采用base64算法进行转码,避免出现中文乱码
        byte[] encryptBytes = Base64.getUrlDecoder().decode(encryptStr);
        byte[] decryptBytes = cipher.doFinal(encryptBytes);
        return new String(decryptBytes);
    }
    public static String decryptBase64(String encryptStr, String decryptKey)
            throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);
        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptKey.getBytes(), "AES"));
        // 采用base64算法进行转码,避免出现中文乱码
        byte[] encryptBytes = Base64.getDecoder().decode(encryptStr);
        byte[] decryptBytes = cipher.doFinal(encryptBytes);
        return new String(decryptBytes);
    }

    public static String encrypt(String content) throws Exception {
        return encrypt(content, KEY);
    }

    public static String decrypt(String encryptStr) throws Exception {
        return decrypt(encryptStr, KEY);
    }

    /**
     * 小金游戏定制版
     * @param content
     * @return
     * @throws Exception
     */
    public static String encryptForXJ(String content) throws Exception {
        return encrypt(content, XJ_KEY);
    }
    /**
     * 小金游戏定制版
     * @param encryptStr
     * @return
     * @throws Exception
     */
    public static Integer decryptForXJ(String encryptStr){
        Integer userId = null;
        try {
            String decrypt = decrypt(encryptStr, XJ_KEY);
            userId = Integer.parseInt(decrypt);
        } catch (Exception e) {
            log.error("===>>XJ authToken 解密失败,encryptStr:{}",encryptStr);
        }
        return userId;
    }


    public static void main(String[] args) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("merchantCode", "mile");
        map.put("customerName", "winktest");
        map.put("token", "ML");
        map.put("client", "PC");
        String content = (new ObjectMapper()).writeValueAsString(map);
        System.out.println("加密前：" + content);

        String encrypt = encrypt(content, KEY);
        System.out.println("加密后：" + encrypt);

        String decrypt = decrypt(encrypt, KEY);
        System.out.println("解密后：" + decrypt);

        String encrypt1 = encryptForXJ("4919");
        log.info("===>>encrypt1:{}",encrypt1);
        log.info("===>>dencrypt1:{}",decryptForXJ(encrypt1));
    }
}
