package com.seektop.fund.payment.henglixingwxpay;

import java.security.MessageDigest;

/**
 * User:
 * DateTime: 2016/10/29 23:24
 */
public class Md5Util {

    /**
     * 32位加密
     */
    public static String md5_32(String plainText){

        StringBuffer buf = null;

        try {

            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte[] b = md.digest();
            int i;
            buf = new StringBuffer("");

            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if(i < 0)
                    i += 256;
                if(i < 16)
                    buf.append("0"); buf.append(Integer.toHexString(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return buf.toString();
    }

    /**
     * 16位加密
     */
    public static String md5_16(String plainText){
        StringBuffer buf = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte[] b = md.digest();
            int i;
            buf = new StringBuffer("");

            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if(i < 0)
                    i += 256;
                if(i < 16)
                    buf.append("0"); buf.append(Integer.toHexString(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return buf.toString().substring(8,24);
    }

}