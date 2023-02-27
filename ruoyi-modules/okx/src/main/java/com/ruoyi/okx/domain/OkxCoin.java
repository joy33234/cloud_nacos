package com.ruoyi.okx.domain;

import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OkxCoin extends CommonEntity {

    private String coin;

    private BigDecimal lowest;

    private BigDecimal hightest;

    private BigDecimal standard;

    private BigDecimal unit;

    private Integer status;

    private boolean isRise;

    private BigDecimal volCcy24h;

    private BigDecimal volUsdt24h;

    private BigDecimal count;
}
