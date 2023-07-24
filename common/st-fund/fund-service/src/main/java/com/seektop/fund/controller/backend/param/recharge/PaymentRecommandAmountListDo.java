package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecommandAmountListDo extends ManageParamBaseDO {

    private Integer page = 0;

    private Integer size = 10;

}
