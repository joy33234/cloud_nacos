package com.seektop.common.local.base.dto;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.function.CommonFunction;
import com.seektop.common.local.base.DataSourceEntity;
import com.seektop.common.local.tools.LanguageLocalSource;
import com.seektop.enumerate.Language;



/**
 * @see com.seektop.enumerate.Language
 * 通用语言参数，适用于一个对象需要多个语言，只开启系统支持的语言
 */
public class LanguageDTO extends JSONObject {

    public void setEn(String en){
        this.put("en",en);
    }

    public void setZh_CN(String zh_cn){
        this.put("zh_CN",zh_cn);
    }

    public void setVietnam(String vietnam){
        this.put("vietnam",vietnam);
    }

    public LanguageLocalSource insert(CommonFunction<Language,DataSourceEntity> normalSupplier){
        final LanguageLocalSource builder = LanguageLocalSource.builder();
        for (Language language : Language.getEnabledLanguage()) {
            builder.add(normalSupplier.execute(language).build(getString(language.getCode())));
        }
        return builder;
    }

    public JSONObject getEnable(){
        JSONObject jsonObject = new JSONObject();
        for (Language language : Language.getEnabledLanguage()) {
            jsonObject.put(language.getCode(),getString(language.getCode()));
        }
        return jsonObject;
    }

    public String getValue(Language language){
        return getString(language.getCode());
    }
}
