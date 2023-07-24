package com.seektop.fund.controller.backend.dto;

import lombok.Data;
import lombok.ToString;


@Data
@ToString
public class ReportFundsCheckPageDto extends ReportFundsCheckDto {

    private Integer page = 1;
    private Integer size = 20;
}
