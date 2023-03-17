package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.Data;

@Data
@TableName
public class OkxAccount extends CommonEntity {
    @TableId (type = IdType.AUTO)
    private Integer id;

    private String name;

    private String apikey;

    private String secretkey;

    private String password;

    private String settingIds;
}
