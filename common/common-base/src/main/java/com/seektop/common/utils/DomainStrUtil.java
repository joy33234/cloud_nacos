package com.seektop.common.utils;


public class DomainStrUtil {

    /**
     * 去除
     * https://www.
     * http://www.
     */
    public static String replacePrefix(String domainStr) {
        domainStr = domainStr.trim();
        domainStr = domainStr.replace("https://www.", "");
        domainStr = domainStr.replace("http://www.", "");
        domainStr = domainStr.replace("https://", "");
        domainStr = domainStr.replace("http://", "");
        return domainStr;
    }

}
