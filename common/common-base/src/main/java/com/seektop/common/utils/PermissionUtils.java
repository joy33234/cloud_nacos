package com.seektop.common.utils;


import java.util.function.Supplier;

/**
 * 新老系统数据权限兼容
 */
public class PermissionUtils {

    private static ThreadLocal<String> VERSION = new ThreadLocal<>();

    /**
     * 设置version
     * @param version
     */
    public static void setVersion(String version){
        VERSION.set(version);
    }

    /**
     * 获取version
     * @return
     */
    public static String getVersion(){
        return VERSION.get();
    }

    /**
     * 区别新旧系统
     */
    public static <T> T checkPermission(Supplier<T> newSystemSupplier, Supplier<T> oldSystemSupplier){

        if("2.0".equals(getVersion())){
            //新系统 直接返回所有的权限
            return newSystemSupplier.get();
        }else {
            //老系统
            return oldSystemSupplier.get();
        }
    }

    /**
     * 防止内存泄漏
     */
    public static void release(){
        VERSION.remove();
    }
}
