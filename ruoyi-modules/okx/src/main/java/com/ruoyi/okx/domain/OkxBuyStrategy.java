package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OkxBuyStrategy extends CommonEntity {
    @TableId (type = IdType.AUTO)
    private Integer id;

    private Integer fallDays;

    private BigDecimal fallPercent;

    private Integer times;

}
