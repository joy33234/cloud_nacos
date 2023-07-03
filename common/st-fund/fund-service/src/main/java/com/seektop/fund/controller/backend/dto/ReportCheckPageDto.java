package com.seektop.fund.controller.backend.dto;

import lombok.Data;
import lombok.ToString;


@Data
@ToString
public class ReportCheckPageDto extends ReportCheckDto {

    private Integer page = 1;   //页码(默认1)
    private Integer size = 20;  //每页数据大小(默认20)
}
