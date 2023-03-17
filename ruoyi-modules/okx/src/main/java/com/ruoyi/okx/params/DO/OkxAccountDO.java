package com.ruoyi.okx.params.DO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ruoyi.common.datasource.form.PageRequestForm;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

@Data
public class OkxAccountDO extends PageRequestForm implements Serializable {


    private Integer id;

    private String name;

    private String apikey;

    private String secretkey;

    private String password;

    private Long[] settingIds;

    private String remark;

    private Map<String, Object> params;




}
