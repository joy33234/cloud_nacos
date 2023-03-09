package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import io.swagger.models.auth.In;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class OkxBuyStrategy extends CommonEntity {
    @TableId
    private Integer id;

    private Integer fallDays;

    private BigDecimal fallPercent;

    private Integer times;

}
