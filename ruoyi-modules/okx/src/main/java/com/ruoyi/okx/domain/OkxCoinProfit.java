package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxCoinProfit extends CommonEntity {

    @TableId (type = IdType.AUTO)
    private Integer id;

    private String coin;

    private Integer accountId;

    private BigDecimal profit;

}
