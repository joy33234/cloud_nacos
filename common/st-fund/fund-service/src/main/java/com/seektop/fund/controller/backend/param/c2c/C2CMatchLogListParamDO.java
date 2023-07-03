package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class C2CMatchLogListParamDO extends ManageParamBaseDO {

    @NotBlank(message = "订单号不能为空")
    private String orderId;

    private Short type;

    private Integer page = 1;

    private Integer size = 20;

}