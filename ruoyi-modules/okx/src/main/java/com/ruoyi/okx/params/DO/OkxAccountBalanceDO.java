package com.ruoyi.okx.params.DO;

import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxAccountBalanceDO extends PageRequestForm implements Serializable {


    private Integer id;

    private String coin;

    private String accountName;

    private Integer accountId;

    private BigDecimal balance;


}
