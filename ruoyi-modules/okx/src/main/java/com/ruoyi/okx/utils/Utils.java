package com.ruoyi.okx.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Utils {


    public static void setScale(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal(0.01)) < 0) {
            amount.setScale(8, RoundingMode.DOWN);
        } else if (amount.compareTo(new BigDecimal(0.1)) < 0) {
            amount.setScale(7, RoundingMode.DOWN);
        }else if (amount.compareTo(new BigDecimal(1)) < 0) {
            amount.setScale(6, RoundingMode.DOWN);
        }else if (amount.compareTo(new BigDecimal(10)) < 0) {
            amount.setScale(5, RoundingMode.DOWN);
        }else if (amount.compareTo(new BigDecimal(100)) < 0) {
            amount.setScale(4, RoundingMode.DOWN);
        }else if (amount.compareTo(new BigDecimal(1000)) < 0) {
            amount.setScale(3, RoundingMode.DOWN);
        }else if (amount.compareTo(new BigDecimal(10000)) < 0) {
            amount.setScale(2, RoundingMode.DOWN);
        }
    }

}
