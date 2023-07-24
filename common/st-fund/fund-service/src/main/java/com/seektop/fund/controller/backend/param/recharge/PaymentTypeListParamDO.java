package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentTypeListParamDO extends ManageParamBaseDO {

    private String coin;

    private Integer page = 1;

    private Integer size = 20;

}