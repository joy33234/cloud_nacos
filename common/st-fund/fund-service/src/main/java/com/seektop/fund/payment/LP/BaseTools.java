package com.seektop.fund.payment.LP;

import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
public class BaseTools {

    public static String base64Encoder(final String str) {
        return base64Encoder(str, "UTF-8");
    }

    // base64编码
    public static String base64Encoder(final String str, String charset) {
        Base64.Encoder en = Base64.getEncoder(); // base64编码
        String encStr = "";
        if (charset == null || "".equals(charset)) {
            encStr = en.encodeToString(str.getBytes());
            return encStr;
        }
        try {
            encStr = en.encodeToString(str.getBytes(charset));
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("LPPayer_base64Encoder失败", ex);
        }

        return encStr;
    }

    // md5编码
    public static String md5(String origin) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            origin = byteArrayToHexString(md.digest(origin.getBytes("UTF-8")));
        } catch (Exception ex) {
            throw new RuntimeException("LPPayer_计算MD5失败", ex);
        }
        return origin;
    }

    public static String byteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2]; // Each byte has two hex characters (nibbles)
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF; // Cast bytes[j] to int, treating as unsigned value
            hexChars[j * 2] = hexArray[v >>> 4]; // Select hex character from upper nibble
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]; // Select hex character from lower nibble
        }
        return new String(hexChars);
    }

}
