package com.seektop.common.utils;

/**
 * Created by ken on 2018/5/14.
 */

public class MoneyUtil {

    public static long yuanToMinMoney(String yuan) {
        if (yuan == null) {
            return 0;
        }
        try {
            int pIdx = yuan.indexOf(".");
            int len = yuan.length();
            String fixed = yuan.replaceAll("\\.", "");
            return Long.valueOf(fixed);

//            if (pIdx < 0 || pIdx == len - 1) {
//                return Long.valueOf(fixed + "0000");
//            } else if (pIdx == len - 2) {
//                return Long.valueOf(fixed + "000");
//            } else if (pIdx == len - 3) {
//                return Long.valueOf(fixed + "00");
//            } else if (pIdx == len - 4) {
//                return Long.valueOf(fixed + "0");
//            } else {
//                return Long.valueOf(fixed.substring(0, pIdx + 4));
//            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static int yuan2Mini(String yuan) {
        if (yuan == null) {
            return 0;
        }
        try {
            int pIdx = yuan.indexOf(".");
            int len = yuan.length();
            String fixed = yuan.replaceAll("\\.", "");
            if (pIdx < 0 || pIdx == len - 1) {
                return Integer.valueOf(fixed + "0000");
            } else if (pIdx == len - 2) {
                return Integer.valueOf(fixed + "000");
            } else if (pIdx == len - 3) {
                return Integer.valueOf(fixed + "00");
            } else if (pIdx == len - 4) {
                return Integer.valueOf(fixed + "0");
            } else {
                return Integer.valueOf(fixed.substring(0, pIdx + 4));
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static long getRepaymentDiscount(long betFinishFlow, long discountRate, long betflowRate) {
        return betFinishFlow * discountRate / betflowRate;
    }


    public static String moneyToYuan(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        if (fen >= 0) {
            return moneyToYuanForPositive(fen);
        } else {
            return "-" + moneyToYuanForPositive(Math.abs(fen));

        }

    }

    public static String moneyToYuanForPositive(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(fen);
        int len = sb.length();
        if (len < 3) {
            for (int i = 0; i < 3 - len; i++) {
                sb.insert(0, "0");
            }
        }
        return sb.insert(sb.length() - 2, ".").toString();
    }
}
