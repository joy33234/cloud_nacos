package com.seektop.common.utils;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 走redis？
 */
public class JobEncryptPermissionUtils {

    private static Supplier<List<Integer>> getJobEncrypt;

    private static ThreadLocal<List<Integer>> JOB_ENCRYPTS = ThreadLocal.withInitial(
            ()->Optional.ofNullable(getJobEncrypt.get()).orElseThrow(()->new IllegalArgumentException("未配置查询权限的方法，请检查配置")));
    public static List<Integer> getJobEncryptPermissions(){
        return JOB_ENCRYPTS.get();
    }
    public static void setJobEncryptPermissions(List<Integer> encryptPermissions){
        JOB_ENCRYPTS.set(encryptPermissions);
    }
    public static void release(){
        JOB_ENCRYPTS.remove();
    }

    public static void setGetJobEncrypt(Supplier<List<Integer>> getJobEncrypt) {
        JobEncryptPermissionUtils.getJobEncrypt = getJobEncrypt;
    }
}
