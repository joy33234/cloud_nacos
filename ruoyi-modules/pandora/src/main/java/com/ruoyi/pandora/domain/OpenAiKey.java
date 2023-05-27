package com.ruoyi.pandora.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 用户对象 sys_user
 * 
 * @author ruoyi
 */
@Data
@TableName
@AllArgsConstructor
@NoArgsConstructor
public class OpenAiKey extends BaseEntity
{
    private static final long serialVersionUID = 1635L;


    /** 用户账号 */
    private String key;

}
