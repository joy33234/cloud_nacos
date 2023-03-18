package com.ruoyi.okx.params.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiseDto {

    /**
     * @see com.ruoyi.common.core.enums.Status
     *
     */
    private Integer status = 0; //0:正常  1：禁用 (交易状态，大盘模式每天仅交易一次)

    private Integer riseCount = 0;

    private BigDecimal risePercent = BigDecimal.ZERO;

    private Integer lowCount = 0;

    private BigDecimal lowPercent = BigDecimal.ZERO;

    private BigDecimal highest = BigDecimal.ZERO;

    private BigDecimal lowest = BigDecimal.ZERO;

    private Boolean riseBought = false; //当日大盘上涨是否交易过

    private Boolean fallBought = false; //当日大盘下跌是否交易过


}
