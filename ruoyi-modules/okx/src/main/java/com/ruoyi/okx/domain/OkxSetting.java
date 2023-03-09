package com.ruoyi.okx.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.annotation.Excel.ColumnType;
import com.ruoyi.common.core.web.domain.CommonEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 参数配置表
 * 
 * @author ruoyi
 */
public class OkxSetting extends CommonEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数主键 */
    @Excel(name = "参数主键", cellType = ColumnType.NUMERIC)
    @TableId
    private Long settingId;

    /** 参数名称 */
    @Excel(name = "参数名称")
    private String settingName;

    /** 参数键名 */
    @Excel(name = "参数键名")
    private String settingKey;

    /** 参数键值 */
    @Excel(name = "参数键值")
    private String settingValue;

    @Transient
    private String desc;



    public Long getSettingId()
    {
        return settingId;
    }

    public void setSettingId(Long settingId)
    {
        this.settingId = settingId;
    }

    @NotBlank(message = "参数名称不能为空")
    @Size(min = 0, max = 100, message = "参数名称不能超过100个字符")
    public String getSettingName()
    {
        return settingName;
    }

    public void setSettingName(String settingName)
    {
        this.settingName = settingName;
    }

    @NotBlank(message = "参数键名长度不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    public String getSettingKey()
    {
        return settingKey;
    }

    public void setSettingKey(String settingKey)
    {
        this.settingKey = settingKey;
    }

    @NotBlank(message = "参数键值不能为空")
    @Size(min = 0, max = 500, message = "参数键值长度不能超过500个字符")
    public String getSettingValue()
    {
        return settingValue;
    }

    public void setSettingValue(String settingValue)
    {
        this.settingValue = settingValue;
    }

    @NotBlank(message = "参数键名长度不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    public String getDesc()
    {
        return desc;
    }

    public void setDesc(String desc)
    {
        this.desc = desc;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("settingId", getSettingId())
            .append("settingName", getSettingName())
            .append("settingKey", getSettingKey())
            .append("settingValue", getSettingValue())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
