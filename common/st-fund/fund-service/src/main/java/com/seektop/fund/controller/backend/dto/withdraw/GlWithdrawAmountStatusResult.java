package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.fund.controller.backend.dto.GlFundUserAccountDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlWithdrawAmountStatusResult extends GlFundUserAccountDO {
    private BigDecimal requireAmount;//所需流水
    private BigDecimal leftAmount;//剩余流水
    private BigDecimal limit;//流水显示倍数
    private String coin;//币种
}
