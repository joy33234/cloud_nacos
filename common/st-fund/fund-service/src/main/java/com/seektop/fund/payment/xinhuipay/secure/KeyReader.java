package com.seektop.fund.payment.xinhuipay.secure;

import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author ty
 * @version 1.0.0
 * @Description <p> 功能描述： 密钥工具类，用于获取银行公私钥，商户公钥等
 * </p>
 * @ModifyBy
 * @since 2016年7月6日
 */
public class KeyReader {

    /**
     * 读取私钥
     *
     * @param keyStr
     * @param base64Encoded
     * @param algorithmName
     * @return
     * @throws InvalidKeySpecException
     */
    public PrivateKey readPrivateKey(String keyStr, boolean base64Encoded,
                                     String algorithmName) throws InvalidKeySpecException {
        return (PrivateKey) readKey(keyStr, false, base64Encoded, algorithmName);
    }

    /**
     * 读取公钥
     *
     * @param keyStr
     * @param base64Encoded
     * @param algorithmName
     * @return
     * @throws InvalidKeySpecException
     */
    public PublicKey readPublicKey(String keyStr, boolean base64Encoded,
                                   String algorithmName) throws InvalidKeySpecException {
        return (PublicKey) readKey(keyStr, true, base64Encoded, algorithmName);
    }

    /**
     * 读取密钥，X509EncodedKeySpec的公钥与PKCS8EncodedKeySpec都可以读取，密钥内容可以为非base64编码过的。
     *
     * @param keyStr
     * @param isPublicKey
     * @param base64Encoded
     * @param algorithmName
     * @return
     * @throws InvalidKeySpecException
     */
    private Key readKey(String keyStr, boolean isPublicKey,
                        boolean base64Encoded, String algorithmName)
            throws InvalidKeySpecException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithmName);

            byte[] encodedKey = keyStr.getBytes("UTF-8");

            if (base64Encoded) {
                encodedKey = Base64.decodeBase64(encodedKey);
            }

            if (isPublicKey) {
                EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);

                return keyFactory.generatePublic(keySpec);
            } else {
                EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);

                return keyFactory.generatePrivate(keySpec);
            }
        } catch (NoSuchAlgorithmException e) {
            // 不可能发生
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }


}