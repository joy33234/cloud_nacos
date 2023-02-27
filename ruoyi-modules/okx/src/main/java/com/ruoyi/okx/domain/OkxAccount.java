package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

@Data
@TableName
public class OkxAccount extends CommonEntity {

    private Integer id;

    private String name;

    private String apikey;

    private String secretkey;

    private String password;
}
