package com.seektop.common.utils;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public class MobileUtils {

    public String getAreaCode(String areaCode) {
        if (StringUtils.isEmpty(areaCode)) {
            return areaCode;
        }
        return areaCode.replaceAll("\\+", "");
    }

    private static final MobileUtils instance = new MobileUtils();

    private MobileUtils() {

    }

    public static MobileUtils getInstance() {
        return instance;
    }
    public static Boolean validate(String areaCode,String mobile){
        switch (areaCode){
            case "86":
               return RegexValidator.isMobile(mobile);
            default:
                return Pattern.matches("^[0-9]*$",mobile);
        }
    }
}
