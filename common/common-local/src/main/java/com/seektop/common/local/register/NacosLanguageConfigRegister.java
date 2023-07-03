package com.seektop.common.local.register;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.context.LanguageLocalContext;
import com.seektop.common.nacos.adapter.base.AbstractBaseConfigChangeAdapter;
import com.seektop.enumerate.Language;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public abstract class NacosLanguageConfigRegister extends AbstractBaseConfigChangeAdapter {


    public abstract Set<NacosLanguageLocalRegister> registerList();

    public Map<String, NacosLanguageLocalRegister> getModule() {
        Map<String, NacosLanguageLocalRegister> map = new HashMap<>();
        final Set<NacosLanguageLocalRegister> nacosLanguageLocalRegisters = registerList();
        for (NacosLanguageLocalRegister nacosLanguageLocalRegister : nacosLanguageLocalRegisters) {
            switch (nacosLanguageLocalRegister.getType()) {
                case DIC:
                    map.put(nacosLanguageLocalRegister.getDataId(),nacosLanguageLocalRegister);
                    break;
                case COMMON:
                    for (Language language : Language.getEnabledLanguage()) {
                        map.put(nacosLanguageLocalRegister.getDataId()+"-"+language.getCode(),nacosLanguageLocalRegister);
                    }
            }
        }
        return map;
    }

    public Set<String> getDataIds() {
        final HashSet<String> ids = new HashSet<>();
        final Set<NacosLanguageLocalRegister> nacosLanguageLocalRegisters = registerList();
        for (NacosLanguageLocalRegister nacosLanguageLocalRegister : nacosLanguageLocalRegisters) {
            switch (nacosLanguageLocalRegister.getType()) {
                case DIC:
                    ids.add(nacosLanguageLocalRegister.getDataId());
                    break;
                case COMMON:
                    for (Language language : Language.getEnabledLanguage()) {
                        ids.add(nacosLanguageLocalRegister.getDataId() + "-" + language.getCode());
                    }
            }
        }
        return ids;
    }


    @Override
    public void onChanged(String dataId, String content) {
        if(StringUtils.isEmpty(content))return;
        Assert.isTrue(!StringUtils.isEmpty(content), dataId+"language config can't be empty");
        final JSONObject object = JSONObject.parseObject(content);
        String format = object.getString("format");
        if (StringUtils.isEmpty(format)) {
            format = "NORMAL";
        }
        switch (format) {
            case "DIC": {
                // 字典结构
                object.remove("format");
                final Map<String, String> map = parseDic("", object);
                LanguageLocalContext.addLanguageConfig(getModule().get(dataId).getModule(),"", map);
            }
            break;
            default:
                // 当前的语言
                final String language = object.getString("language");
                Assert.isTrue(!StringUtils.isEmpty(language), "language config can't be empty");
                object.remove("language");
                object.remove("format");
                final Map<String, String> map = parseJson(language, object);
                LanguageLocalContext.addLanguageConfig(getModule().get(dataId).getModule(),language,map);
        }

    }


    public static Map<String, String> parseJson(String parentKey, JSONObject jsonObject) {
        Map<String, String> result = new HashMap<>();
        Set<String> keys = jsonObject.keySet();
        keys.parallelStream().forEach(key -> {
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                JSONObject valueJsonObject = (JSONObject) value;
                result.putAll(parseJson(parentKey + "_" + key, valueJsonObject));
            } else if (value instanceof JSONArray) {

            } else {
                result.put(parentKey + "_" + key, value.toString());
            }
        });
        return result;
    }

    public static Map<String, String> parseDic(String parentKey, JSONObject jsonObject) {
        Map<String, String> result = new HashMap<>();
        Set<String> keys = jsonObject.keySet();
        keys.parallelStream().forEach(key -> {
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                JSONObject valueJsonObject = (JSONObject) value;
                result.putAll(parseDic(parentKey + "_" + key, valueJsonObject));
            } else if (value instanceof JSONArray) {

            } else {
                result.put(key + "" + parentKey, value.toString());
            }
        });
        return result;
    }

}
