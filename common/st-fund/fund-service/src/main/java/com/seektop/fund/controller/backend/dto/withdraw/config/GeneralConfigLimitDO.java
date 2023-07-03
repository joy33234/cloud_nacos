package com.seektop.fund.controller.backend.dto.withdraw.config;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
public class GeneralConfigLimitDO implements Serializable {

    @NotNull(message = "用户层级")
    private Set<Integer> levelIds;

    @NotNull(message = "限额最小值")
    @Min(value = 100, message = "限额最小值不能小于100")
    private BigDecimal minAmount;

    @NotNull(message = "限额最大值")
    @Min(value = 100, message = "限额最大值不能小于100")
    private BigDecimal maxAmount;

}
