package com.seektop.common.local.base;

import com.seektop.enumerate.Language;



public interface DataSourceEntity {


    void setModule(String module);

    void setLanguage(String code);

    void setConfigKey(String format);

    void setConfigValue(String configValue);

    default DataSourceEntity buildKey(LocalKeyConfig localKeyConfig, Language language,String... param){
        setConfigKey(String.format(localKeyConfig.getKey(),param));
        setLanguage(language.getCode());
        setModule(localKeyConfig.getModule());
        return this;
    }

    default DataSourceEntity build(String configValue){
        setConfigValue(configValue);
        return this;
    }

}
