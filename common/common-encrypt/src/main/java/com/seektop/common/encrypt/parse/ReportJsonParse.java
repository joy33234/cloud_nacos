package com.seektop.common.encrypt.parse;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class ReportJsonParse implements ResultParse<JSONObject> {
    @Override
    public JSONObject parse(JSONObject source, Map<String, List<Function<String, String>>> encrypts) {
        JSONObject data = (JSONObject) source.get("data");
        if (null == data) {
            return source;
        }
        JSONArray list = (JSONArray) data.get("list");
        if (CollectionUtils.isEmpty(list)) {
            return source;
        }
        list.forEach(value -> {
            encrypts.forEach((fieldName, parses) -> {
                try {
                    JSONObject jsonObject = (JSONObject) value;
                    Object o = jsonObject.get(fieldName);
                    if (null == o) {
                        return;
                    }
                    String apply = o.toString();
                    for (Function parse : parses) {
                        apply = parse.apply(apply).toString();
                    }
                    jsonObject.put(fieldName, apply);
                } catch (Exception e) {
                    log.error("脱敏失败--->c：{}  异常：{}", source, e);
                }
            });
        });
        return source;
    }
}
