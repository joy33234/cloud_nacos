package com.ruoyi.system.mapper;

import com.ruoyi.system.api.domain.SysDept;
import com.ruoyi.system.api.domain.SysUserAi;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门管理 数据层
 * 
 * @author ruoyi
 */
public interface SysAiMapper
{

    /**
     * 根据userID查询信息
     * 
     * @param userId
     * @return 信息
     */
    public SysUserAi selectAiByUserId(Long userId);

}
