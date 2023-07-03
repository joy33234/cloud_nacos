package com.seektop.common.encrypt.aspect;


import com.google.common.collect.Lists;
import com.seektop.common.encrypt.EncryptComponent;
import com.seektop.common.encrypt.annotation.Encrypt;
import com.seektop.common.encrypt.annotation.EncryptField;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.encrypt.parse.ResultParse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;


@Aspect
@Slf4j
public class EncryptAspect {

    @Autowired
    private EncryptComponent encryptComponent;

    @Around("@annotation(com.seektop.common.encrypt.annotation.Encrypt)")
    public Object encrypt(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        try {
            Object proceed = proceedingJoinPoint.proceed();
            List<Integer> jobEncrypts = encryptComponent.getJobEncrypt();
            //每一个属性对应的脱敏器
            HashMap<String,List<Function>>  fieldParseMap= new HashMap<>();
            //初始化脱敏器
            //获取需要脱敏的字段
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            Method method = signature.getMethod();
            Encrypt annotation = method.getAnnotation(Encrypt.class);
            EncryptField[] values = annotation.values();
            for (EncryptField value : values) {
                ArrayList<Function> parses = Lists.newArrayList();
                fieldParseMap.put(value.fieldName(),parses);
                for (EncryptTypeEnum encryptTypeEnum : value.typeEnums()) {
                    if(!jobEncrypts.contains(encryptTypeEnum.getJobEncryptId())){
                        parses.add(encryptTypeEnum.getParse());
                    }
                }
            }
            if(proceed ==null) return null;
            try {
                //获取结果解析
                Class<? extends ResultParse> aClass = annotation.resultParse();
                if(!aClass.isInterface()){
                    ResultParse resultParse = aClass.newInstance();
                    return resultParse.parse(proceed,fieldParseMap);
                }
            }catch (Exception e){
                return proceed;
            }
            return proceed;
        } catch (Throwable throwable) {
            throw throwable;
        }
    }
}
