package com.seektop.common.local.base.parse;

import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.constant.enums.LanguageParserEnums;

public interface  LocalFreemarkerConfig extends LocalKeyConfig {

    @Override
    default LanguageParserEnums getParseType(){
        return LanguageParserEnums.FREEMARKER;
    }
}
