package com.ruoyi.okx.params.DO;


import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class BuyRecordDO extends PageRequestForm implements Serializable {
    private static final long serialVersionUID = 4370115008163389854L;

    private Integer id;

    private String coin;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal amount;

    protected Date startTime;

    protected Date endTime;

    private String accountName;

    private Integer status;
}