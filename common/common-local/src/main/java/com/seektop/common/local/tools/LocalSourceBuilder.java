package com.seektop.common.local.tools;

import com.seektop.common.local.base.LocalKeyConfig;
import lombok.Getter;

@Getter
public class LocalSourceBuilder {

    private LocalKeyConfig localKeyConfig;

    private String[] param;

    private String value;

    public static LocalSourceBuilder key(LocalKeyConfig localKeyConfig){
        final LocalSourceBuilder localSourceBuilder = new LocalSourceBuilder();
        localSourceBuilder.localKeyConfig = localKeyConfig;
        return localSourceBuilder;
    }

    public  LocalSourceBuilder withParam(String... param){
        this.param = param;
        return this;
    }

    public  LocalSourceBuilder configValue(String value){
        this.value = value;
        return this;
    }
}