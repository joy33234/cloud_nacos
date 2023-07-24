package com.seektop.common.utils;

import com.seektop.constant.AppType;
import com.seektop.constant.OSType;
import org.springframework.util.ObjectUtils;

public class ProductUtils {

    /**
     * 通过系统类型和应用类型获取产品类型
     *
     * @param osType
     * @param appType
     * @return
     */
    public static int getProductType(Integer osType, Integer appType) {
        if (ObjectUtils.isEmpty(osType)) {
            osType = 0;
        }
        if (ObjectUtils.isEmpty(appType)) {
            appType = 0;
        }
        int productId = 0;
        switch (appType) {
            case AppType.CASH:
                productId = getCashAppProductId(osType);
            break;
            case AppType.PROXY:
                productId = getProxyAppProductId(osType);
            break;
            case AppType.SPORT:
                productId = getSportAppProductId(osType);
            break;
        }
        return productId;
    }

    /**
     * 获取代理的产品ID
     *
     * @param osType
     * @return
     */
    private static int getProxyAppProductId(Integer osType) {
        int productId = 0;
        switch (osType) {
            case OSType.PC:
                productId = 4;
            break;
            case OSType.IOS:
                productId = 5;
            break;
            case OSType.ANDROID:
                productId = 6;
            break;
        }
        return productId;
    }

    /**
     * 获取体育的产品ID
     *
     * @param osType
     * @return
     */
    private static int getSportAppProductId(Integer osType) {
        int productId = 0;
        switch (osType) {
            case OSType.IOS:
                productId = 7;
            break;
            case OSType.ANDROID:
                productId = 8;
            break;
        }
        return productId;
    }

    /**
     * 获取现金网的产品ID
     *
     * @param osType
     * @return
     */
    private static int getCashAppProductId(Integer osType) {
        int productId = 0;
        switch (osType) {
            case OSType.PC:
                productId = 1;
            break;
            case OSType.IOS:
                productId = 2;
            break;
            case OSType.ANDROID:
                productId = 3;
            break;
            case OSType.H5:
                productId = 9;
            break;
        }
        return productId;
    }

}