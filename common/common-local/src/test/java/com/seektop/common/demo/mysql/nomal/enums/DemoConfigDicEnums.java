package com.seektop.common.demo.mysql.nomal.enums;

import com.seektop.common.local.base.parse.LocalCommonConfig;
import com.seektop.common.local.constant.enums.LanguageSourceEnums;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 继承 LocalCommonConfig 表示使用普通解析器
 */
@Getter
@AllArgsConstructor
public enum DemoConfigDicEnums implements LocalCommonConfig {

    /**
     * 配置内容成字典或者普通模式，可以在注册的时候进行切换
     * demo中会用两个配置说明字典和普通方式的配置
     */
    NACOS_NORMAL("NACOS_NORMAL_DEMO"),
    NACOS_NORMAL_DEMO_PARAM("NACOS_NORMAL_DEMO_PARAM_%s"),
    NACOS_NORMAL_MULTIPART_PARAM("NACOS_NORMAL_MULTIPART_PARAM_%s_%s"),
    NACOS_NORMAL_RESULT("NACOS_NORMAL_RESULT"),
    ;
    private String key;

    /**
     * 防止项目里面多个多语言配置相同的key冲突，进行区分
     * 改字段需要与注册的时候配置的模块内容相同
     * @return
     */
    @Override
    public String getModule() {
        return "nacos-normal-dic";
    }

    @Override
    public LanguageSourceEnums getDataSource() {
        return LanguageSourceEnums.MYSQL;
    }

}
