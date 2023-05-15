package com.ruoyi.system.service;

import com.ruoyi.system.api.domain.SysUserAi;

/**
 * 部门管理 服务层
 * 
 * @author ruoyi
 */
public interface ISysAiService
{


    /**
     * 根据userId查询信息
     * 
     * @param userId 部门ID
     * @return 部门信息
     */
    public SysUserAi selectAiByUserId(Long userId);


}
