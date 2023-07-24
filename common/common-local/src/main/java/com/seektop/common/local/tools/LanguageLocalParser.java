package com.seektop.common.local.tools;

import com.seektop.common.function.CommonFunction;
import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.base.dto.LanguageDTO;
import com.seektop.common.local.constant.enums.LanguageParserEnums;
import com.seektop.common.local.context.LanguageDataSourceContext;
import com.seektop.common.local.context.LanguageLocalContext;
import com.seektop.common.local.context.LanguageParseContext;
import com.seektop.enumerate.Language;
import com.seektop.exception.GlobalException;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

public class LanguageLocalParser {

    private LocalKeyConfig key;

    private String[] param;

    private String defaultValue;

    private String errorValue;


    public String parse(Language language,Object ...value){
        try {
        final LanguageParserEnums parseType = key.getParseType();
        String configKey = "";
        switch (key.getDataSource()){
            case MYSQL:
                configKey = LanguageDataSourceContext.getConfigValue(key,language,param);
                break;
            case NACOS:
                if(null != param && param.length >0){
                    configKey = LanguageLocalContext.getConfigKey(language.getCode(), key,String.format(key.getKey(),param));
                }else {
                    configKey = LanguageLocalContext.getConfigKey(language.getCode(), key);
                }
                break;
        }

        if(StringUtils.isEmpty(configKey)){
            if(null != defaultValue){
                return defaultValue;
            }
            return key.getKey();
        }

            return LanguageParseContext.find(parseType).parse(configKey,value);
        }catch (Exception e){
            if(null != errorValue){
                return errorValue;
            }
            if(null != defaultValue){
                return defaultValue;
            }
            return key.getKey();
        }
    }

    public static LanguageLocalParser key(LocalKeyConfig key){
        final LanguageLocalParser languageLocalParser = new LanguageLocalParser();
        languageLocalParser.key = key;
        return languageLocalParser;
    }

    public LanguageLocalParser withParam(String... param){
        this.param = param;
        return this;
    }

    public LanguageLocalParser withDefaultValue(String defaultValue){
        this.defaultValue = defaultValue;
        return this;
    }

    public LanguageLocalParser withErrorValue(String errorValue){
        this.errorValue = errorValue;
        return this;
    }

    public LanguageDTO  parse(Collection<Language> languages, Object ...value){
        LanguageDTO languageDTO = new LanguageDTO();
        for (Language language : languages) {
            final String parse = parse(language, value);
            languageDTO.put(language.getCode(),parse);
        }
        return languageDTO;
    }
}
