package com.ruoyi.system.service.impl;

import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.datascope.annotation.DataScope;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.domain.SysDept;
import com.ruoyi.system.api.domain.SysRole;
import com.ruoyi.system.api.domain.SysUser;
import com.ruoyi.system.api.domain.SysUserAi;
import com.ruoyi.system.domain.vo.TreeSelect;
import com.ruoyi.system.mapper.SysAiMapper;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.service.ISysAiService;
import com.ruoyi.system.service.ISysDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门管理 服务实现
 * 
 * @author ruoyi
 */
@Service
public class SysAiServiceImpl implements ISysAiService
{
    @Autowired
    private SysAiMapper aiMapper;

    /**
     * 根据部门ID查询信息
     * 
     * @param userId 部门ID
     * @return 部门信息
     */
    @Override
    public SysUserAi selectAiByUserId(Long userId)
    {
        return aiMapper.selectAiByUserId(userId);
    }


}
