package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class C2CEggStopParamDO extends ManageParamBaseDO {

    @NotNull(message = "彩蛋记录ID不能为空")
    private Integer recordId;

}