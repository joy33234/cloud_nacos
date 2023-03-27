package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@TableName
@AllArgsConstructor
@NoArgsConstructor
public class OkxAccountBalance extends CommonEntity {
    @TableId (type = IdType.AUTO)
    private Integer id;

    private Integer accountId;

    private String accountName;

    private String coin;

    private BigDecimal balance;

}
