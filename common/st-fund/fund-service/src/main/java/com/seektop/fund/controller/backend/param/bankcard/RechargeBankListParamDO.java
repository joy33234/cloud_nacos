package com.seektop.fund.controller.backend.param.bankcard;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RechargeBankListParamDO extends ManageParamBaseDO {

    private String coin;

    private String bankName;

    private Integer page = 1;

    private Integer size = 10;

}