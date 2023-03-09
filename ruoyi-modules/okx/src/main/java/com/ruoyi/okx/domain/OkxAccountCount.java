package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 帐户币种余额
 */
@Data
public class OkxAccountCount extends CommonEntity {
    @TableId
    private Integer id;

    private Integer accountId;

    private String coin;

    private BigDecimal count;
}
