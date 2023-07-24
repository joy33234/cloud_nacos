package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

@Data
public class BalanceDto implements Serializable {

    @NotNull(message = "userId不能为空")
    private Integer userId;

    private String coinCode = "CNY";

    private Date startTime;

    private Date endTime;

}