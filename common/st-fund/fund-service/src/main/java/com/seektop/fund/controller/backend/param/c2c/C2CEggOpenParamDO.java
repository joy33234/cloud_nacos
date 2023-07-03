package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Getter
@Setter
public class C2CEggOpenParamDO extends ManageParamBaseDO {

    @NotNull(message = "彩蛋类型不能为空")
    private Short eggType;

    @NotNull(message = "活动时长不能为空")
    private Integer duration;

    private Date startDate;

}