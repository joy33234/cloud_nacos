package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
public class ValidMerchantAccountForm extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 8364872268472929573L;
    /**
     * 自动出款的id
     */
    private Integer conditionId;
}
