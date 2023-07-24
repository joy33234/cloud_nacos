package com.seektop.common.utils;

public class RandomValidateCodeUtil {

    /**
     * 生产4位随机数
     *
     * @return
     */
    public String getRandomNumber() {
        return (int) ((Math.random() * 9 + 1) * 1000)+"";
    }

    /**
     * 生产6位随机数
     *
     * @return
     */
    public String get6RandomNumber() {
        return (int) ((Math.random() * 9 + 1) * 100000)+"";
    }

    private static final RandomValidateCodeUtil instance = new RandomValidateCodeUtil();

    private RandomValidateCodeUtil() {

    }

    public static RandomValidateCodeUtil getInstance() {
        return instance;
    }

}