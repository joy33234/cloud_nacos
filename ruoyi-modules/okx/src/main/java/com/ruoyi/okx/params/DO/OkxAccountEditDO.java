package com.ruoyi.okx.params.DO;

import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class OkxAccountEditDO extends PageRequestForm implements Serializable {


    private Integer id;

    private String name;

    private Integer status;

    private Long[] settingIds;

    private String remark;

    private Map<String, Object> params;




}
