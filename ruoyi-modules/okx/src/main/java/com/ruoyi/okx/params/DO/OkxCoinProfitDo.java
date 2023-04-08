package com.ruoyi.okx.params.DO;


import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxCoinProfitDo extends PageRequestForm implements Serializable {

    private Integer accountId;

    private String coin;

    private Integer id;

}
