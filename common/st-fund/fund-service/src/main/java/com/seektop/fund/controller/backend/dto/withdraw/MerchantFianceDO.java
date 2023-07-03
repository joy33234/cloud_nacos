package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFianceDO {

    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;
    @NotNull(message = "参数异常:dailyLimit Not Null")
    private Integer dailyLimit;
    private BigDecimal singleLimit;
    private Integer bankId;
    private String bankName;
    private String bankCardNo;
    private String bankAccountName;
    private String bankBranchName;


}
