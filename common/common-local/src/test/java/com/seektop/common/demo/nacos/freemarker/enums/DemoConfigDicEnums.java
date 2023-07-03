package com.seektop.common.demo.nacos.freemarker.enums;

import com.seektop.common.local.base.parse.LocalCommonConfig;
import com.seektop.common.local.base.parse.LocalFreemarkerConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 继承 LocalFreemarkerConfig 表示使用FreeMarker
 */
@Getter
@AllArgsConstructor
public enum DemoConfigDicEnums implements LocalFreemarkerConfig {

    /**
     * 配置内容成字典或者普通模式，可以在注册的时候进行切换
     * demo中会用两个配置说明字典和普通方式的配置
     */
    NACOS_NORMAL_TITILE("NACOS_NORMAL_TITLE"),
    NACOS_NORMAL_BODY("NACOS_NORMAL_BODY"),
    ;
    private String key;

    /**
     * 防止项目里面多个多语言配置相同的key冲突，进行区分
     * 改字段需要与注册的时候配置的模块内容相同
     * @return
     */
    @Override
    public String getModule() {
        return "nacos-normal-free";
    }

}
