package com.seektop.fund.controller.backend.param.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawBalanceQueryDO extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 5111585141349788421L;

    /**
     * 商户ID
     */
    private Integer channelId = -1;

    /**
     * 商户号
     */
    private String merchantCode;

    /**
     * 商户状态
     */
    private Integer status = -1;

    private Integer page;

    private Integer size;

    private String coin;
}
