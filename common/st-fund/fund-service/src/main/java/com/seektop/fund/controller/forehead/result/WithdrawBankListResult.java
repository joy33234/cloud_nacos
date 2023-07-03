package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class WithdrawBankListResult implements Serializable {

    private Integer bankId;

    private String bankName;

    private String bankLogo;

    private String coin;

}