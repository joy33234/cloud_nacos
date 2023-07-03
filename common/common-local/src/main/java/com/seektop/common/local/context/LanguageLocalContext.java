package com.seektop.common.local.context;

import com.seektop.common.local.base.LocalKeyConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageLocalContext {

    private final static ConcurrentHashMap<String,ConcurrentHashMap<String,String>> languageConfigMap = new ConcurrentHashMap<>();

    public static synchronized void addLanguageConfig(String name,String key,String value){
        final ConcurrentHashMap<String, String> config = new ConcurrentHashMap<>();
        config.put(key,value);
        final ConcurrentHashMap<String, String> nameConfig = languageConfigMap.putIfAbsent(name,config);
        if(null != nameConfig) {
            nameConfig.put(key, value);
        }
    }

    public static synchronized void addLanguageConfig(String name,String language ,Map<String,String> map){
        final ConcurrentHashMap<String, String> config = new ConcurrentHashMap<>();
        config.putAll(map);
        final ConcurrentHashMap<String, String> nameConfig = languageConfigMap.putIfAbsent(name,config);
        if(null != nameConfig) {
            if(StringUtils.isEmpty(language)){
                nameConfig.clear();
            }
            nameConfig.putAll(map);
        }
    }

    public static String getConfigKey(String language, LocalKeyConfig config){
        final ConcurrentHashMap<String, String> stringStringConcurrentHashMap = languageConfigMap.get(config.getModule());
        return stringStringConcurrentHashMap.get(language+"_"+config.getKey());
    }

    public static String getConfigKey(String language, LocalKeyConfig config,String key){
        final ConcurrentHashMap<String, String> stringStringConcurrentHashMap = languageConfigMap.get(config.getModule());
        return stringStringConcurrentHashMap.get(language+"_"+key);
    }

}
