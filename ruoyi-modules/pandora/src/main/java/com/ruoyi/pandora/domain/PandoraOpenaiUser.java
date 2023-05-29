package com.ruoyi.pandora.domain;

import com.baomidou.mybatisplus.annotation.TableId;
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
public class PandoraOpenaiUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @TableId
    private Long userId;


    /** 用户账号 */
    private String userName;

    /**
     * 部门ID
     *  type  0：按次数计费   1：包月    2：包年
     * */
    private Integer type;


    /** 数量 */
    private Integer count;


    public PandoraOpenaiUser(Long userId)
    {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("userName", getUserName())
            .append("type", getType())
            .append("count", getCount())
            .toString();
    }
}
