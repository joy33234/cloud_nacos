package com.seektop.common.utils;


public class TokenUtils {
    private static ThreadLocal<String> LOCAL_TOKEN = new ThreadLocal<>();

    public static void setToken(String token){
        LOCAL_TOKEN.set(token);
    }
    public static String getToken(){
        String token = LOCAL_TOKEN.get();
        return token;
    }
    public static void release(){
        LOCAL_TOKEN.remove();
    }
}
