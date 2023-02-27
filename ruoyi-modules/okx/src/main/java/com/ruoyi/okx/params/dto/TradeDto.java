package com.ruoyi.okx.params.dto;


import com.ruoyi.okx.domain.OkxAccount;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TradeDto implements Serializable {
    private static final long serialVersionUID = -1994978549958227801L;

    private String coin;

    private String instId;

    private BigDecimal sz;

    private BigDecimal px;

    private String side;

    private Integer buyStrategyId;

    private Integer sellStrategyId;

    private BigDecimal unit;

    private Integer buyRecordId;

    private Integer times;

    private OkxAccount account;

    private String ordType;
}