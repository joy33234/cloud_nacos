package com.seektop.fund.payment.zhihuipay;


import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class AESUtils {
    private static final String key = "koCEknoP71PGKACTgySYQqaYT0HSeraUjmy1946VkIZfQX1fTvzWGDTdJuNj7RmZ";

    //private static final String initVector = "10059";

    private static String byte2hex(byte[] b) {
        String hs = "";
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            stmp = (Integer.toHexString(b[n] & 0XFF));
            if (stmp.length() == 1) hs = hs + "0" + stmp;
            else hs = hs + stmp;
        }
        return hs;
    }
    //initVector:商户ID
    public static String encrypt(String value , String initVector,String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(initVector.getBytes("UTF-8"));


            IvParameterSpec iv = new IvParameterSpec(byte2hex(Arrays.copyOf(encodedhash, 8)).getBytes());

            digest = MessageDigest.getInstance("SHA-256");
            encodedhash = digest.digest(key.getBytes("UTF-8"));


            SecretKeySpec skeySpec = new SecretKeySpec(byte2hex(Arrays.copyOf(encodedhash, 16)).getBytes(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            byte[] encrypted = cipher.doFinal(value.getBytes());

            return new String(Base64.getEncoder().encode(Base64.getEncoder().encodeToString(encrypted).getBytes()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
