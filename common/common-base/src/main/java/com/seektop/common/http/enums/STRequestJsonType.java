package com.seektop.common.http.enums;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.PropertyNamingStrategy;
import com.alibaba.fastjson.serializer.SerializeConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

@Getter
@AllArgsConstructor
public enum STRequestJsonType {
    DEFAULT(v -> (JSONObject) JSON.toJSON(v), "普通模式[小写]"),
    UPPER(v -> (JSONObject) JSON.toJSON(v, CONFIGS.upperConfig), "首字母大写");
    Function<Object, JSONObject> parser;
    private String desc;

    final static class CONFIGS {
        static SerializeConfig upperConfig = new SerializeConfig();
        static {
            upperConfig.propertyNamingStrategy = PropertyNamingStrategy.PascalCase;
        }
    }
}
