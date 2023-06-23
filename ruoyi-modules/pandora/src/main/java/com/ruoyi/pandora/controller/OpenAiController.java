package com.ruoyi.pandora.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.file.FileTypeUtils;
import com.ruoyi.common.core.utils.file.MimeTypeUtils;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.pandora.business.OpenAiBusiness;
import com.ruoyi.pandora.domain.PandoraOpenaiUser;
import com.ruoyi.pandora.params.Dto.ChatLog;
import com.ruoyi.system.api.RemoteFileService;
import com.ruoyi.system.api.domain.SysFile;
import com.ruoyi.system.api.model.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
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
     * chatGpt 聊天
     */
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping(value = "/chat")
    public R<?> updateChat(@Validated @RequestBody ChatLog chatLog) {
        return openAiBusiness.updateChat(chatLog);
    }


    /**
     * chatGpt 校正输入
     */
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping(value = "/fix")
    public R<?> fix(@Validated @RequestBody ChatLog chatLog) {
        return openAiBusiness.fix(chatLog);
    }


    /**
     * chatGpt 生成图片
     */
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping(value = "/images")
    public R<?> getImages(@Validated @RequestBody ChatLog chatLog) {
        return openAiBusiness.images(chatLog);
    }


    /**
     * 图片上传
     */
    @Log(title = "图片上传", businessType = BusinessType.UPDATE)
    @PostMapping("/images/upload")
    public R<?> upload(@RequestParam("file") MultipartFile file) {
        return openAiBusiness.imagesUpload(file);
    }
}
