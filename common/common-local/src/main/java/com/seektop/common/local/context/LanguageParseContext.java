package com.seektop.common.local.context;

import com.seektop.common.local.base.LanguageParse;
import com.seektop.common.local.compoent.parse.LocalCommonParser;
import com.seektop.common.local.compoent.parse.LocalFreemarkerParser;
import com.seektop.common.local.constant.enums.LanguageParserEnums;

import java.util.HashMap;
import java.util.Map;

public class LanguageParseContext {
    private final static Map<String, LanguageParse> parseMap = new HashMap();
    static {
        parseMap.put("COMMON",new LocalCommonParser());
        parseMap.put("FREEMARKER",new LocalFreemarkerParser());
    }
    public static LanguageParse find(LanguageParserEnums parserEnums){
        return parseMap.get(parserEnums.getName());
    }
}
