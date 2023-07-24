package com.seektop.common.utils;


public class UserIdUtils {
    private static ThreadLocal<Integer> LOCAL_USER_ID = new ThreadLocal<>();

    public static void setUserId(Integer token){
        LOCAL_USER_ID.set(token);
    }
    public static Integer getUserId(){
        Integer token = LOCAL_USER_ID.get();
        return token;
    }
    public static void release(){
        LOCAL_USER_ID.remove();
    }
}
