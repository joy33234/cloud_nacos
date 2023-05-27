package com.ruoyi.pandora.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.pandora.business.OpenAiBusiness;
import com.ruoyi.pandora.domain.PandoraOpenaiUser;
import com.ruoyi.pandora.params.Dto.ChatLog;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/ai")
public class OpenAiController extends BaseController
{

    @Resource
    private OpenAiBusiness openAiBusiness;

    /**
     * 新增参数配置
     */
    @RequiresPermissions("system:ai")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult ai() {
        PandoraOpenaiUser sysUserAi = openAiBusiness.selectAiByUserId(SecurityUtils.getUserId());
        if (sysUserAi == null || (sysUserAi.getType() == 0 && sysUserAi.getCount() <= 0)) {
            return error("试用已结束，请充值。");
        }
        String res = HttpUtil.post("url",new HashMap<>());
        return success(res);
    }




    /**
     * 新增参数配置
     */
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping(value = "/chat")
    public R<?> updateChat(@Validated @RequestBody ChatLog chatLog) {
        return openAiBusiness.updateChat(chatLog);
    }
}
