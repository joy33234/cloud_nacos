package com.seektop.common.local.base.parse;

import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.constant.enums.LanguageParserEnums;

public interface LocalCommonConfig extends LocalKeyConfig {

    @Override
    default LanguageParserEnums getParseType(){
        return LanguageParserEnums.COMMON;
    }
}
