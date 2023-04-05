package com.ruoyi.okx.params.dto;

import com.ruoyi.okx.domain.OkxCoinProfit;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AccountProfitDto {

    private Integer accountId;

    private BigDecimal profit;

    private List<OkxCoinProfit> coinProfits;
}
