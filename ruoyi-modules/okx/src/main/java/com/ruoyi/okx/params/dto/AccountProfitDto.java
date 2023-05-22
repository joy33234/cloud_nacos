package com.ruoyi.okx.params.dto;

import com.ruoyi.okx.domain.OkxCoinProfit;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AccountProfitDto {

    private Integer accountId;

    private String accountName;

    /**
     * 总盈亏
     */
    private BigDecimal profit = BigDecimal.ZERO;

    /**
     * 交易完成订单盈亏
     */
    private BigDecimal finishProfit = BigDecimal.ZERO;

    /**
     * 未完成订单当前盈亏
     */
    private BigDecimal unFinishProfit = BigDecimal.ZERO;


}
