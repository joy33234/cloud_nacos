package com.seektop.common.local.compoent.parse;

import com.seektop.common.local.base.LanguageParse;

public class LocalCommonParser implements LanguageParse {

    @Override
    public String parse(String key, Object... content) {
        return content==null ? key : String.format(key,content);
    }
}
