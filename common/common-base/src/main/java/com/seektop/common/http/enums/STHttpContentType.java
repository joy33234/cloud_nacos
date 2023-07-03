package com.seektop.common.http.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum STHttpContentType {

    APPLICATION_JSON("application/json"),
    FORM("application/x-www-form-urlencoded");

    private String contentType;
}
