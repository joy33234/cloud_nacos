package com.seektop.fund.payment.huyun;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by hyberbin on 2016/10/26.
 */
public class SecureUtil {

    public static byte[] digestMD5(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return digest("md5", msg.getBytes(encoding));
    }

    public static byte[] digest(String algorithm, byte[] bytes)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest alga = MessageDigest.getInstance(algorithm);
        alga.update(bytes);
        return alga.digest();
    }

    public static String MD5String(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return byte2hex(digestMD5(msg, encoding)).toUpperCase();
    }

    public static String SHA256String(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return byte2hex(digest("SHA-256", msg.getBytes(encoding))).toUpperCase();
    }

    public static byte[] generateAesKey(String s) {
        try {
            KeyGenerator e = KeyGenerator.getInstance("AES");
            SecretKey secretKey = e.generateKey();
            return secretKey.getEncoded();
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    public static byte[] AESEncrypt(byte[] key, byte[] data) throws Exception {
        String algorithm = "AES";
        SecretKeySpec deskey = new SecretKeySpec(key, algorithm);
        // 根据给定的字节数组和算法构造一个密钥
        Cipher c1 = Cipher.getInstance(algorithm, new BouncyCastleProvider());
        c1.init(Cipher.ENCRYPT_MODE, deskey);
        if (algorithm.endsWith("NoPadding")) {
            return c1.doFinal(paddingZero(data, 16));
        } else {
            return c1.doFinal(data);
        }
    }

    public static byte[] paddingZero(byte[] dataBytes, int len) {
        if (dataBytes.length % len != 0) {
            int fillLength = len - dataBytes.length % len;
            byte[] fillBytes = new byte[fillLength];
            for (int i = 0; i < fillLength; i++) {
                fillBytes[i] = 0x00;
            }
            dataBytes = ArrayUtils.addAll(dataBytes, fillBytes);
        }
        return dataBytes;
    }

    public static String SHA1String(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return byte2hex(digest("SHA-1", msg.getBytes(encoding))).toUpperCase();
    }

    public static String hmac(String aValue, String aKey)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte k_ipad[] = new byte[64];
        byte k_opad[] = new byte[64];
        byte keyb[];
        byte value[];
        try {
            keyb = aKey.getBytes("UTF-8");
            value = aValue.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            keyb = aKey.getBytes();
            value = aValue.getBytes();
        }

        Arrays.fill(k_ipad, keyb.length, 64, (byte) 54);
        Arrays.fill(k_opad, keyb.length, 64, (byte) 92);
        for (int i = 0; i < keyb.length; i++) {
            k_ipad[i] = (byte) (keyb[i] ^ 0x36);
            k_opad[i] = (byte) (keyb[i] ^ 0x5c);
        }

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {

            return null;
        }
        md.update(k_ipad);
        md.update(value);
        byte dg[] = md.digest();
        md.reset();
        md.update(k_opad);
        md.update(dg, 0, 16);
        dg = md.digest();
        return byte2hex(dg);
    }

    public static String BASE64_EN(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return Base64.encode(msg.getBytes(encoding));
    }

    public static String BASE64_EN_Byte(byte[] bytes, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return Base64.encode(bytes);
    }

    public static byte[] BASE64_DE_Byte(String base64) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return Base64.decode(base64);
    }

    public static String URL_EN(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return URLEncoder.encode(msg, encoding);
    }

    public static String URL_DN(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return URLDecoder.decode(msg, encoding);
    }

    public static String BASE64_DE(String msg, String encoding)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return new String(Base64.decode(msg), encoding);
    }

    public static String BASE64_2HEX(String msg) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return byte2hex(Base64.decode(msg));
    }

    public static String generateDesKey(String s) {
        try {
            KeyGenerator e = KeyGenerator.getInstance("DES");
            SecretKey secretKey = e.generateKey();
            return Base64.encode(secretKey.getEncoded());
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    public static String byte2hex(byte[] b) {
        String hs = "";
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0xFF);
            if (stmp.length() == 1)
                hs = hs + "0" + stmp;
            else
                hs = hs + stmp;
        }
        return hs;
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    /**
     * Convert char to byte
     *
     * @param c char
     * @return byte
     */
    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static String utf82gbk(String utf) {
        String l_temp = utf8ToUnicode(utf);
        l_temp = Unicode2GBK(l_temp);

        return l_temp;
    }

    /**
     * @param dataStr
     * @return String
     */

    public static String Unicode2GBK(String dataStr) {
        int index = 0;
        StringBuffer buffer = new StringBuffer();

        int li_len = dataStr.length();
        while (index < li_len) {
            if (index >= li_len - 1 || !"\\u".equals(dataStr.substring(index, index + 2))) {
                buffer.append(dataStr.charAt(index));

                index++;
                continue;
            }

            String charStr = "";
            charStr = dataStr.substring(index + 2, index + 6);

            char letter = (char) Integer.parseInt(charStr, 16);

            buffer.append(letter);
            index += 6;
        }

        return buffer.toString();
    }

    /**
     * 字符串转换unicode
     */
    public static String utf8ToUnicode(String string) {

        StringBuffer unicode = new StringBuffer();

        for (int i = 0; i < string.length(); i++) {

            // 取出每一个字符
            char c = string.charAt(i);

            // 转换为unicode
            unicode.append("\\u" + Integer.toHexString(c));
        }

        return unicode.toString();
    }

    /**
     * unicode转换字符串
     */
    public static String unicodeToUtf8(String unicode) {
        if (StringUtils.isEmpty(unicode)) {
            return null;
        }
        String str = "";
        String[] hex = unicode.split("//u");
        System.out.println(hex.length);
        for (int i = 1; i < hex.length; i++) {
            int data = Integer.parseInt(hex[i], 16);
            str += (char) data;
        }
        return str;
    }

}
