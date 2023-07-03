package com.seektop.fund.controller.backend.param.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Set;

@Getter
@Setter
public class USDTWithdrawConfigSubmitParamDO extends ManageParamBaseDO {

    /**
     * 设置类型
     *
     * 1：代理
     * 0：会员
     */
    @NotNull(message = "设置类型不能为空")
    private Short type;

    /**
     * 提现状态
     *
     * 0 全部用户可提现
     * 1 全部用户不可提现
     * 2 仅充值过的用户可提现
     */
    @NotNull(message = "提现状态不能为空")
    private Integer status;

    /**
     * USDT提现支持的钱包协议
     */
    private Set<String> protocols;

}