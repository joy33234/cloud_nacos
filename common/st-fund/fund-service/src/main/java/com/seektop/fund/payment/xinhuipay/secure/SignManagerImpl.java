package com.seektop.fund.payment.xinhuipay.secure;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;


public class SignManagerImpl implements SignManager {
    private Logger logger = Logger.getLogger(SignManagerImpl.class.getName());

    private KeyReader keyReaderDemo;

    /**
     * 非对称密钥校验签名
     *
     * @param key
     * @param content
     * @param signature
     * @param algorithmsName
     * @param algorithms
     * @return
     */
    @Deprecated
    public boolean keyPairCheck(String key, String content, String signature,
                                String algorithmsName, String algorithms) {
        PublicKey pubKey = null;
        try {
            pubKey = keyReaderDemo.readPublicKey(key, true, algorithmsName);

        } catch (InvalidKeySpecException e) {

            logger.error("error!", e);
        }
        return keyPairCheck(pubKey, content, signature, algorithms);

    }

    @Deprecated
    public boolean keyPairCheck(PublicKey pubKey, String content, String signature,
                                String algorithms) {
        try {
            byte[] signed = Base64.decodeBase64(signature.getBytes(DEFAULT_CHARSET));
            Signature signCheck = Signature.getInstance(algorithms);
            signCheck.initVerify(pubKey);
            signCheck.update(content.getBytes(DEFAULT_CHARSET));
            return signCheck.verify(signed);
        } catch (InvalidKeyException e) {
            logger.error("error!", e);
        } catch (UnsupportedEncodingException e) {
            logger.error("error!", e);
        } catch (NoSuchAlgorithmException e) {
            logger.error("error!", e);
        } catch (SignatureException e) {
            logger.error("error!", e);
        }
        return false;
    }

    /**
     * 非对称密钥签名
     *
     * @param content
     * @param keyStr
     * @param algorithmsName
     * @param algorithms
     * @return
     */
    public String keyPairSign(String content, String keyStr, String algorithmsName,
                              String algorithms) {

        PrivateKey priKey = null;
        try {
            priKey = keyReaderDemo.readPrivateKey(keyStr, true, algorithmsName);

        } catch (InvalidKeySpecException e) {
            logger.error("error!", e);
        }
        return keyPairSign(content, priKey, algorithms);
    }

    public String keyPairSign(String content, PrivateKey priKey, String algorithms) {

        try {
            Signature signature = Signature.getInstance(algorithms);
            signature.initSign(priKey);
            signature.update(content.getBytes(DEFAULT_CHARSET));
            return new String(Base64.encodeBase64(signature.sign(), false), DEFAULT_CHARSET);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | UnsupportedEncodingException e) {
            logger.error("error!", e);
        }

        return null;
    }

    public String md5Sign(String content, String keyStr) {
        try {
            return DigestUtils.md5Hex((content + keyStr).getBytes(DEFAULT_CHARSET));
        } catch (UnsupportedEncodingException e) {
        }
        return null;
    }

    public String sign(String content, String signType, PrivateKey priKey) {
        if (signType.equals(RSA_ALGORITHM_NAME)) {
            return keyPairSign(content, priKey, RSA_SIGN_ALGORITHMS);
        } else if (signType.equals(DSA_ALGORITHM_NAME)) {
            return keyPairSign(content, priKey, DSA_SIGN_ALGORITHMS);

        }
        return null;
    }

    public String sign(String content, String signType, String key) {
        if (signType.equals(RSA_ALGORITHM_NAME)) {
            return keyPairSign(content, key, RSA_ALGORITHM_NAME, RSA_SIGN_ALGORITHMS);
        } else if (signType.equals(MD5_ALGORITHM_NAME)) {
            md5Sign(content, key);
        } else if (signType.equals(DSA_ALGORITHM_NAME)) {
            return keyPairSign(content, key, DSA_ALGORITHM_NAME, DSA_SIGN_ALGORITHMS);
        }
        System.out.println();
        return null;
    }

    public boolean check(String signature, String content, String signType, String key) {
        if (signType.equals(RSA_ALGORITHM_NAME)) {
            return keyPairCheck(key, content, signature, RSA_ALGORITHM_NAME, RSA_SIGN_ALGORITHMS);
        } else if (signType.equals(MD5_ALGORITHM_NAME)) {
            return signature.equals(md5Sign(content, key));
        } else if (signType.equals(DSA_ALGORITHM_NAME)) {
            return keyPairCheck(key, content, signature, DSA_ALGORITHM_NAME, DSA_SIGN_ALGORITHMS);

        }
        return false;
    }

    public boolean check(String signature, String content, String signType, PublicKey pubKey) {
        if (signType.equals(RSA_ALGORITHM_NAME)) {
            return keyPairCheck(pubKey, content, signature, RSA_SIGN_ALGORITHMS);
        } else if (signType.equals(DSA_ALGORITHM_NAME)) {
            return keyPairCheck(pubKey, content, signature, DSA_SIGN_ALGORITHMS);

        }
        return false;
    }

}
