package com.seektop.common.csvexport.model;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


@Getter
public class FieldMapBuilder<T> {
    List<FieldMap<T>> fieldMaps;
    public static <P> FieldMapBuilder<P> builder(Class<P> clazz){
        FieldMapBuilder<P> fieldMapBuilder = new FieldMapBuilder<>();
        List<FieldMap<P>> fieldMaps = new ArrayList<>();
        fieldMapBuilder.fieldMaps=fieldMaps;
        return fieldMapBuilder;
    }
    public FieldMapBuilder<T> add(String title, Function<T,Object> parse){
        fieldMaps.add(
                FieldMap.<T>builder()
                .fieldTitle(title)
                .parse(parse)
                .build()
        );
        return this;
    }
}
