package com.seektop.common.local.register;

import com.google.common.collect.Sets;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import org.springframework.context.annotation.Configuration;

import java.util.Set;


@Configuration
public class CommonLanguageConfigRegister extends NacosLanguageConfigRegister{


    @Override
    public Set<NacosLanguageLocalRegister> registerList() {
        return Sets.newHashSet(
                NacosLanguageLocalRegister.builder()
                        .module("result-code-dic")
                        .dataId("st-result-code-dic")
                        .type(RegisterTypeEnums.DIC)
                        .build()
        );
    }
}
