package com.seektop.common.local.base;

import com.seektop.common.local.constant.enums.LanguageParserEnums;
import com.seektop.base.LocalKey;
import com.seektop.common.local.constant.enums.LanguageSourceEnums;

public interface LocalKeyConfig extends LocalKey {

    /**
     * 配置所属的模块
     * @return
     */
    String getModule();

    /**
     * 配置的key
     * @return
     */
    String getKey();

    /**
     * 解析方式
     * @return
     */
    LanguageParserEnums getParseType();

    /**
     * 数据源
     * @return
     */
    default LanguageSourceEnums getDataSource(){
        return LanguageSourceEnums.NACOS;
    }


    default String getConfigKey(){
        return getModule()+getKey();
    }
}
