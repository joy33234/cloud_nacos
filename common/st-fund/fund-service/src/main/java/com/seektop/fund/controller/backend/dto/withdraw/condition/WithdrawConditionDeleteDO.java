package com.seektop.fund.controller.backend.dto.withdraw.condition;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class WithdrawConditionDeleteDO implements Serializable {

    /**
     * 主键ID
     */
    @NotNull(message = "参数异常:id Not Null")
    private Integer id;

}
