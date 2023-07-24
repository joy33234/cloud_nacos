package com.seektop.common.utils;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.security.SecureRandom;

public class DESEncrypt {

    private Cipher cipher;

    public static String encrypt(String plainText, String key) throws Exception {
        SecureRandom sr = new SecureRandom();
        DESKeySpec dks = new DESKeySpec(key.getBytes());
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey securekey = keyFactory.generateSecret(dks);
        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
        byte[] encryptedData = cipher.doFinal(plainText.getBytes());
        return Base64.encodeBase64String(encryptedData).replaceAll("\\s*", "");
    }

    public static String decrypt(String input, String key) throws Exception {
        SecureRandom sr = new SecureRandom();
        DESKeySpec dks = new DESKeySpec(key.getBytes());
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey securekey = keyFactory.generateSecret(dks);
        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.DECRYPT_MODE, securekey, sr);
        byte[] result = cipher.doFinal(Base64.decodeBase64(input));
        return new String(result);
    }

    public DESEncrypt(String key) throws Exception {
        SecureRandom sr = new SecureRandom();
        DESKeySpec dks = new DESKeySpec(key.getBytes());
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey securekey = keyFactory.generateSecret(dks);
        cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
    }

    public String encrypt(String plainText) throws Exception {
        byte[] encryptedData = cipher.doFinal(plainText.getBytes());
        return Base64.encodeBase64String(encryptedData).replaceAll("\\s*", "");
    }

}