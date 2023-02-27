package com.ruoyi.okx.params.DO;

import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.Data;

import java.io.Serializable;

@Data
public class OkxAccountDO extends PageRequestForm implements Serializable {
    private Integer id;

    private String apikey;

    private String secretkey;

    private String password;
}
