package com.seektop.fund.handler.validation;

import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Validator {

    private List<DataValidation> list = new ArrayList<>();

    /**
     * 添加校验
     * @param validation
     * @return
     */
    public Validator add(DataValidation validation){
        list.add(validation);
        return this;
    }

    /**
     * 添加校验
     * @param expression 校验不通过表达式
     * @param message 提示消息
     * @return
     */
    public Validator add(Boolean expression, String message){
        return add(() -> {
            if(expression) {
                throw new GlobalException(message);
            }
        });
    }

    /**
     * 添加校验
     * @param expression 校验不通过表达式
     * @param resultCode 提示消息
     * @return
     */
    public Validator add(Boolean expression, ResultCode resultCode){
        return add(() -> {
            if(expression) {
                throw new GlobalException(resultCode);
            }
        });
    }

    /**
     * 添加校验
     * @param supplier 校验不通过表达式
     * @param message 提示消息
     * @return
     */
    public Validator add(Supplier<Boolean> supplier, String message) {
        return add(supplier.get(), message);
    }

    public Validator valid() throws GlobalException {
        for (DataValidation validation : list) {
            validation.valid();
        }
        list.clear();
        return this;
    }

    public static Validator build(){
        return new Validator();
    }
}
