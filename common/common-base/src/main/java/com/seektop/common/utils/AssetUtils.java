package com.seektop.common.utils;

import com.seektop.common.function.NormalFunction;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;

public class AssetUtils {
    public static void isMobile(String areaCode,String mobile,String message) throws GlobalException {
        if (!MobileUtils.validate(areaCode,mobile)) {
            throw new GlobalException(ResultCode.INVALID_PARAM,message);
        }
    }

    /**
     * 检查表达式是否成立，成立则抛出异常
     * @param expression 表达式
     * @param message 提示信息
     * @throws GlobalException 抛出异常
     */
    public static void isNotAllow(boolean expression, String message) throws GlobalException {
        if(expression) {
            throw new GlobalException(ResultCode.INVALID_PARAM, message);
        }
    }

    public static void isNotAllow(boolean expression, String message, NormalFunction function) throws GlobalException {
        if(expression) {
            function.execute();
            throw new GlobalException(ResultCode.INVALID_PARAM, message);
        }
    }
}
