package com.seektop.common.csvexport.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CsvExportAnnotation {

    String name();
    String tableName();
}
