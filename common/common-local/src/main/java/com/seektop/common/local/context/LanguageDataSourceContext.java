package com.seektop.common.local.context;

import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.register.DataBaseLanguageConfigRegister;
import com.seektop.enumerate.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//@Component
public class LanguageDataSourceContext {

    private static DataBaseLanguageConfigRegister dataBaseLanguageConfigRegister;

    @Autowired
    public void setDataBaseLanguageConfigRegister(DataBaseLanguageConfigRegister dataBaseLanguageConfigRegister){
        LanguageDataSourceContext.dataBaseLanguageConfigRegister = dataBaseLanguageConfigRegister;
    }

    public static DataBaseLanguageConfigRegister getDataBaseLanguageConfigRegister(){
        return dataBaseLanguageConfigRegister;
    }

    public static String getConfigValue(LocalKeyConfig localKeyConfig, Language language, String... param){
        // 使用本地缓存
        String key = String.format(localKeyConfig.getKey(),param);
        final String configKey = LanguageDataSourceContext.getDataBaseLanguageConfigRegister().get(
                localKeyConfig.getModule(),
                key,
                language.getCode()
        );
        return configKey;
    }
}
