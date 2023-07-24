package com.seektop.common.csvexport.model;


import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;


@Getter
@Builder
public class FieldMap<T> {

    private String fieldName;
    private String fieldTitle;
    private Function<T,Object> parse;
}
