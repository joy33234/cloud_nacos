package com.seektop.fund.payment.jintaopay;

/**
 * Created by Dell on 2018/12/13.
 */


import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import java.io.*;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RSACryptoUtil {
    /**
     * 加密算法RSA
     */
    public static final String KEY_ALGORITHM = "RSA";
    /**
     * 填充方式
     */
    public static final String CIPHER_TRANSFORMAT = "RSA/ECB/PKCS1Padding";

    /**
     * RSA最大加密明文大小
     */
    private static final int MAX_ENCRYPT_BLOCK = 245;

    /**
     * RSA最大解密密文大小
     */
    private static final int MAX_DECRYPT_BLOCK = 256;

    /**
     * 公钥加密
     * @param content  原文
     * @param publicKey  公钥
     * @param inputCharset 字符编码
     * @return  加密后的字符串（base64）
     * @throws Exception
     */
    public static String encryptByPublicKey(String content, String publicKey, String inputCharset)
            throws Exception {

        byte[] data = content.getBytes(inputCharset);
        byte[] keyBytes = Base64.decodeBase64(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);

        // 对数据加密
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMAT);
        cipher.init(Cipher.ENCRYPT_MODE, publicK);
        int inputLen = data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;
        // 对数据分段加密
        while (inputLen - offSet > 0) {
            if (inputLen - offSet > MAX_ENCRYPT_BLOCK) {
                cache = cipher.doFinal(data, offSet, MAX_ENCRYPT_BLOCK);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * MAX_ENCRYPT_BLOCK;
        }
        byte[] encryptedData = out.toByteArray();
        out.close();

        return Base64.encodeBase64String(encryptedData);
    }

    /**
     * 私钥解密
     * @param content  密文（base64）
     * @param privateKey  私钥
     * @param inputCharset  字符编码
     * @return
     * @throws Exception
     */
    public static String decryptByPrivateKey(String content,
                                             String privateKey, String inputCharset) throws Exception {

        byte[] encryptedData = Base64.decodeBase64(content);
        byte[] keyBytes = Base64.decodeBase64(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMAT);
        cipher.init(Cipher.DECRYPT_MODE, privateK);
        int inputLen = encryptedData.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;
        // 对数据分段解密
        while (inputLen - offSet > 0) {
            if (inputLen - offSet > MAX_DECRYPT_BLOCK) {
                cache = cipher
                        .doFinal(encryptedData, offSet, MAX_DECRYPT_BLOCK);
            } else {
                cache = cipher
                        .doFinal(encryptedData, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * MAX_DECRYPT_BLOCK;
        }
        byte[] decryptedData = out.toByteArray();
        out.close();
        return new String(decryptedData, inputCharset);
    }

    /**
     * 从文件中加载密钥字符串
     * @return 是否成功
     * @throws Exception
     */
    public static String loadKeyString(String keyFile){
        String keyString="";
        InputStream in=null;
        BufferedReader br=null;
        try {
            in= RSACryptoUtil.class.getResourceAsStream("/"+keyFile);
            br= new BufferedReader(new InputStreamReader(in));
            String readLine= null;
            StringBuilder sb= new StringBuilder();
            while((readLine= br.readLine())!=null){
                if(readLine.charAt(0)=='-'){
                    continue;
                }else{
                    sb.append(readLine);
                    sb.append('\r');
                }
            }
            keyString=sb.toString();
        } catch (IOException e) {
        } catch (Exception e) {

        }finally{
            if(br!=null){
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
            if(in!=null){
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
        return keyString;
    }

    /**
     * 从文件中加载密钥字符串  根据文件路径加载
     * @return 是否成功
     * @throws Exception
     */
    public static String loadKeyStringByPath(String keyFile){
        String keyString="";
        InputStream in=null;
        BufferedReader br=null;
        try {
            in = new FileInputStream(keyFile);
            br= new BufferedReader(new InputStreamReader(in));
            String readLine= null;
            StringBuilder sb= new StringBuilder();
            while((readLine= br.readLine())!=null){
                if(readLine.charAt(0)=='-'){
                    continue;
                }else{
                    sb.append(readLine);
                    sb.append('\r');
                }
            }
            keyString=sb.toString();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println(e.getMessage());

        }finally{
            if(br!=null){
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
            if(in!=null){
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
        return keyString;
    }

   
}
