package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OkxCoinTicker extends CommonEntity {
    @TableId (type = IdType.AUTO)
    private Integer id;

    private String coin;

    private String instId;

    private BigDecimal last;

    private BigDecimal open24h;

    private BigDecimal low24h;

    private BigDecimal high24h;

    private BigDecimal average;

    private BigDecimal monthAverage;

    private BigDecimal ins;

    private BigDecimal monthIns;
}
