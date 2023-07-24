package com.seektop.fund.controller.backend.result.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class WithdrawBankSettingResult implements Serializable {

    private static final long serialVersionUID = -1231322318540949067L;

    private Integer bankId;

    private String bankName;

    /**
     * 银行维护状态: 1-开启、0-关闭
     */
    private Integer status;

    private String coin;
}
