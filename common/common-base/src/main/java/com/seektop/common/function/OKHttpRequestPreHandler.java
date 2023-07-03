package com.seektop.common.function;

import com.alibaba.fastjson.JSONObject;

@FunctionalInterface
public interface OKHttpRequestPreHandler {
    void pre(JSONObject param, JSONObject header, String url);
}
