package com.seektop.common.http.enums;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Type;

@Getter
@AllArgsConstructor
public enum STRResultParser {
    DEFAULT(null,"默认"),
    JSON_IGNORE_UPPER(JSONObject::parseObject, "普通模式[小写]"),
    ;
    ResultParser<String,Type,Object> parser;
    private String desc;


    @FunctionalInterface
    public interface ResultParser<T,S,R>{
        R parse(T param,S returnType);
    }
}
