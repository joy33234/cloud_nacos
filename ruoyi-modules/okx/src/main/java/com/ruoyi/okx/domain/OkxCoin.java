package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;

@Data
public class OkxCoin extends CommonEntity {
    @TableId
    private String coin;

    private BigDecimal lowest;

    private BigDecimal hightest;

    private BigDecimal standard;

    private BigDecimal unit;

    private Integer status;

    private boolean isRise;

    private BigDecimal volCcy24h;

    private BigDecimal volUsdt24h;

    @TableField(exist = false)
    private BigDecimal balance;

    @TableField(exist = false)
    private BigDecimal btcIns;

    @TableField(exist = false)
    private BigDecimal tradeMinAmount;

    @TableField(exist = false)
    private BigDecimal last;

    @TableField(exist = false)
    private String boughtAccountIds;
}
