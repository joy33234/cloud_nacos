package com.seektop.common.demo.nacos.nomal.config;

import com.google.common.collect.Sets;
import com.seektop.common.demo.nacos.nomal.enums.DemoConfigDicEnums;
import com.seektop.common.demo.nacos.nomal.enums.DemoConfigNormalEnums;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import com.seektop.common.local.register.NacosLanguageConfigRegister;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class DemoConfig extends NacosLanguageConfigRegister {
    @Override
    public Set<NacosLanguageLocalRegister> registerList() {
        return Sets.newHashSet(

                NacosLanguageLocalRegister.builder()
                        /**
                         * 对应注册枚举配置的module
                         * @see DemoConfigNormalEnums#getModule()
                         */
                        .module("nacos-normal")
                        /**
                         * 对应nacos dataid
                         */
                        .dataId("demo-nacos-normal")
                        /**
                         * common模式：
                         * 一个语言对应一个配置文件
                         */
                        .type(RegisterTypeEnums.COMMON)
                        .build(),

                NacosLanguageLocalRegister.builder()
                        /**
                         * @see DemoConfigDicEnums#getModule()
                         */
                        .module("nacos-normal-dic")
                        /**
                         * 对应nacos dataid
                         */
                        .dataId("demo-nacos-normal-dic")
                        /**
                         * 字典模式
                         * 多个语言对应一个配置文件
                         */
                        .type(RegisterTypeEnums.DIC)
                        .build()
        );
    }
}
