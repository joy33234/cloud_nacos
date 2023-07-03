package com.seektop.fund.controller.backend.param.withdraw;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class WithdrawAlarmDto {

    public String ip;

    @NotNull(message = "timeStamp is Not Null")
    public String timeStamp;

    @NotNull(message = "sign is Not Null")
    public String sign;
}
