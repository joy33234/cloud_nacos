package com.seektop.common.local.config;

import com.seektop.common.local.base.LanguageConfigRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NacosLanguageLocalRegister implements LanguageConfigRegister {

    /**
     * nacos
     */
    private String dataId;

    /**
     * 模块
     */
    private String module;

    /**
     * 配置类型
     */
    private RegisterTypeEnums type;

}
