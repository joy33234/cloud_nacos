package com.seektop.common.utils;

import java.math.BigDecimal;

/**
 * BigDecima工具
 * @Author blake
 * @Date 2019/8/15 15:13
 **/
public class BigDecimalUtils {

    public final static BigDecimal TEN_THOUSAND = new BigDecimal(10000);
    public final static BigDecimal HUNDRED = new BigDecimal(100);


    /**
     * 判断非空，并默认为0
     * @param in
     * @return
     */
    public static BigDecimal ifNullSet0(BigDecimal in) {
        if (in != null) {
            return in;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 重写求和方法
     * @param in
     * @return
     */
    public static BigDecimal sum(BigDecimal ...in){
        BigDecimal result = BigDecimal.ZERO;
        for (int i = 0; i < in.length; i++){
            result = result.add(ifNullSet0(in[i]));
        }
        return result;
    }

    /**
     * 判断是否为BigDecimal
     * @param str
     * @return
     */
    public static boolean isBigDecimal(String str) {
        if (str == null || str.trim().length() == 0) {
            return false;
        }
        char[] chars = str.toCharArray();
        int sz = chars.length;
        int i = (chars[0] == '-') ? 1 : 0;
        if (i == sz) return false;

        if (chars[i] == '.') return false;//除了负号，第一位不能为'小数点'

        boolean radixPoint = false;
        for (; i < sz; i++) {
            if (chars[i] == '.') {
                if (radixPoint) return false;
                radixPoint = true;
            } else if (!(chars[i] >= '0' && chars[i] <= '9')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 小于0
     * @param amount
     * @return
     */
    public static boolean lessThanZero(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * 大于0
     * @param amount
     * @return
     */
    public static boolean moreThanZero(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
