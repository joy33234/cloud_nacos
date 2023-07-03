package com.seektop.common.csvexport.enums;



public enum  DatasourceTypeEnum {
    ES("es",2000,10000,"ES数据源"),
    MYSQL("MYSQL",2000,200000,"msql数据源");

    private String name;
    private Integer  defaultPageSize;
    private Integer  defaultTotal;
    private String remark;

    DatasourceTypeEnum(String name, Integer defaultPageSize, Integer defaultTotal, String remark) {
        this.name = name;
        this.defaultPageSize = defaultPageSize;
        this.defaultTotal = defaultTotal;
        this.remark = remark;
    }

    public String getName() {
        return name;
    }

    public Integer getDefaultPageSize() {
        return defaultPageSize;
    }

    public Integer getDefaultTotal() {
        return defaultTotal;
    }

    public String getRemark() {
        return remark;
    }
}
