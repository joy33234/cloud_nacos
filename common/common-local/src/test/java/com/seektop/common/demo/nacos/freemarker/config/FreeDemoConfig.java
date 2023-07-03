package com.seektop.common.demo.nacos.freemarker.config;

import com.google.common.collect.Sets;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import com.seektop.common.local.register.NacosLanguageConfigRegister;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class FreeDemoConfig extends NacosLanguageConfigRegister {
    @Override
    public Set<NacosLanguageLocalRegister> registerList() {
        return Sets.newHashSet(

                NacosLanguageLocalRegister.builder()
                        /**
                         * 对应注册枚举配置的module
                         * @see com.seektop.common.demo.nacos.freemarker.enums.DemoConfigDicEnums#getModule()
                         */
                        .module("nacos-normal-free")
                        /**
                         * 对应nacos dataid
                         */
                        .dataId("demo-nacos-freemarker")
                        /**
                         * common模式：
                         * 一个语言对应一个配置文件
                         */
                        .type(RegisterTypeEnums.DIC)
                        .build()
        );
    }
}
