package com.seektop.common.local.compoent.parse;

import com.seektop.common.freemarker.ParserUtils;
import com.seektop.common.local.base.LanguageParse;

import java.io.IOException;

public class LocalFreemarkerParser implements LanguageParse {

    /**
     * 模板先不做缓存
     * @param key
     * @param content
     * @return
     */
    @Override
    public String parse(String key, Object... content) {
        try {
            return ParserUtils.parse(key,content[0]);
        } catch (IOException e) {
            return key;
        }
    }
}
