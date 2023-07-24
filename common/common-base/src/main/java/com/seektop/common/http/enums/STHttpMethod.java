package com.seektop.common.http.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum STHttpMethod {

    POST("POST"),
    GET("GET");

    private String method;
}
