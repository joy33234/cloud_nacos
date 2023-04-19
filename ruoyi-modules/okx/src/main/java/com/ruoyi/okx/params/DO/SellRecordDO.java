package com.ruoyi.okx.params.DO;

import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class SellRecordDO extends PageRequestForm implements Serializable {
    private static final long serialVersionUID = 4077057172046411467L;

    private Integer id;

    private String coin;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal amount;

    protected Date beginTime;

    protected Date endTime;

    private Integer status;

    private String accountName;
}