package com.seektop.common.encrypt.parse;

import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface ResultParse<T> {
    /**
     * 从特殊的返回值类型获取到可以解析的数据
     * @param source
     * @return
     */
    T parse(T source, Map<String, List<Function<String, String>>> encrypts);


    default void encryptObject(Map<String, List<Function>> fieldParseMap, Object v) {
        fieldParseMap.forEach((fieldName,parses)->{
            try {
                Class<?> aClass = v.getClass();
//                Field declaredField = aClass.getDeclaredField(fieldName);
                Field declaredField = ReflectionUtils.findField(aClass,fieldName);
                declaredField.setAccessible(true);
                Object o = declaredField.get(v);
                if(ObjectUtils.isEmpty(o))return;
                String result = o.toString();
                for (Function parse : parses) {
                    result = parse.apply(result).toString();
                }
                declaredField.set(v,result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
