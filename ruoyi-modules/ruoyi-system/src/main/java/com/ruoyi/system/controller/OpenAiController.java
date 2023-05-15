package com.ruoyi.system.controller;

import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.api.domain.SysUser;
import com.ruoyi.system.api.domain.SysUserAi;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysAiService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/ai")
public class OpenAiController extends BaseController
{
    @Autowired
    private ISysAiService aiService;

    /**
     * 新增参数配置
     */
    @RequiresPermissions("system:ai")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult ai(@Validated @RequestBody SysConfig config)
    {
        SysUserAi sysUserAi = aiService.selectAiByUserId(SecurityUtils.getUserId());
        if (sysUserAi == null || (sysUserAi.getType() == 0 && sysUserAi.getCount() <= 0)) {
            return error("试用已结束，请充值。");
        }

        String res = HttpUtil.post("url",new HashMap<>());
        return success(res);
    }

}
