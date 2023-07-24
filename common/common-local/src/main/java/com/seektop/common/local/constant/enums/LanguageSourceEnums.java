package com.seektop.common.local.constant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LanguageSourceEnums {
    NACOS("NACOS"),
    MYSQL("MYSQL");

    private String name;
}
