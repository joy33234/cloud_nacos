package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class OkxBuyRecord extends CommonEntity {
    @TableId (type = IdType.AUTO)
    private Integer id;

    private String coin;

    private String instId;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal amount;

    private BigDecimal fee;

    private BigDecimal feeUsdt;

    private Integer status;

    private String orderId;

    private String okxOrderId;

    private Integer strategyId;

    private Integer times;

    private Integer accountId;

    private String accountName;

    private Integer marketStatus;

    private String modeType;

    /**
     * 当前价格
     */
    @Transient
    @TableField(exist = false)
    private BigDecimal last;

    /**
     * 当前价格
     */
    @Transient
    @TableField(exist = false)
    private BigDecimal profit;

}
