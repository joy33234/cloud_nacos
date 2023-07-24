package com.seektop.fund.payment.henglixingwxpay;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * AES加密工具类
 */
public class AESEncryptUtil {

    /**
     * 初始化 AES Cipher
     *
     * @param password
     * @param isEncryptMode
     * @return
     */
    public static Cipher initAESCipher(String password, boolean isEncryptMode) {

        try {

            IvParameterSpec zeroIv = new IvParameterSpec(password.getBytes("utf-8"));

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            final SecretKeySpec secretKey = new SecretKeySpec(password.getBytes("utf-8"), "AES");

            if (isEncryptMode) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, zeroIv);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, zeroIv);
            }

            return cipher;

        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 加密
     *
     * @param bytes    需要加密的内容
     * @param password 加密密码
     * @return
     */
    public static byte[] encrypt(byte[] bytes, String password) {

        try {

            Cipher cipher = initAESCipher(password, true);

            if (null == cipher) {
                return bytes;
            }

            return cipher.doFinal(bytes); // 加密
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 解密
     *
     * @param content  待解密内容
     * @param password 解密密钥
     * @return
     */
    public static byte[] decrypt(byte[] content, String password) {

        try {

            Cipher cipher = initAESCipher(password, false);

            if (null == cipher) {
                return content;
            }

            return cipher.doFinal(content);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }



    /**
     * eas 数据加密
     *
     * @param data
     * @param secret
     * @return
     */
    public static String encrypt(String data, String secret) throws Exception {

        //1,进行aes加密
        byte[] bytes = AESEncryptUtil.encrypt(data.getBytes("utf-8"), secret);

        //2,再进行base64编码,将字节转换成字符串
        return Base64Util.encode(bytes);

    }

    /**
     * eas 数据解密
     *
     * @param data
     * @param secret
     * @return
     */
    public static String decrypt(String data, String secret) throws Exception {

        //1,进行base64解码
        byte[] bytes = Base64Util.decode(data);

        //2,进行aes解密
        byte[] result = AESEncryptUtil.decrypt(bytes, secret);

        return new String(result, "utf-8");

    }

}
