package com.seektop.fund.configuration;

import com.google.common.collect.Sets;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import com.seektop.common.local.register.NacosLanguageConfigRegister;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class FundLocalLanguageConfiguration extends NacosLanguageConfigRegister {

    @Override
    public Set<NacosLanguageLocalRegister> registerList() {
        return Sets.newHashSet(
                NacosLanguageLocalRegister.builder()
                        .module("fund-dic")
                        .dataId("st-fund-dic")
                        .type(RegisterTypeEnums.DIC)
                        .build(),
                NacosLanguageLocalRegister.builder()
                        .module("fund-mvc")
                        .dataId("st-fund-mvc")
                        .type(RegisterTypeEnums.COMMON)
                        .build()
        );
    }
}
