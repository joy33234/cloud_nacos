package com.seektop.common.local.configuration;

import com.seektop.common.local.base.LanguageConfigRegister;
import com.seektop.common.local.base.LanguageLocalConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class LanguageLocalBaseConfiguration implements LanguageLocalConfiguration {

    @Bean
    public List<LanguageLocalConfiguration> languageConfigRegisters(){
        return new ArrayList<>();
    }

    @Override
    public void languageConfigRegisters(List<LanguageConfigRegister> languageConfigRegisters) {

    }
}
