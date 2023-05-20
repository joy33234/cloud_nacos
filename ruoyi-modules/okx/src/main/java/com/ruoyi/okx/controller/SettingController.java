package com.ruoyi.okx.controller;

import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.service.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/setting")
public class SettingController extends BaseController
{
    @Autowired
    private SettingService settingService;

    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:setting:list")
    @GetMapping("/list")
    public TableDataInfo list(OkxSetting setting)
    {
        startPage();
        List<OkxSetting> list = settingService.selectSettingList(setting);
        list.stream().forEach(item -> item.setDesc(item.getSettingName() + "-" + item.getSettingValue()));
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("okx:setting:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, OkxSetting setting)
    {
        List<OkxSetting> list = settingService.selectSettingList(setting);
        ExcelUtil<OkxSetting> util = new ExcelUtil<OkxSetting>(OkxSetting.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @GetMapping(value = "/{settingId}")
    public AjaxResult getInfo(@PathVariable Long settingId)
    {
        return success(settingService.selectSettingById(settingId));
    }

    /**
     * 根据参数键名查询参数值
     */
    @GetMapping(value = "/settingKey/{settingKey}")
    public AjaxResult getSettingKey(@PathVariable String settingKey)
    {
        return success(settingService.selectSettingByKey(settingKey));
    }

    /**
     * 新增参数配置
     */
    @RequiresPermissions("okx:setting:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OkxSetting setting)
    {
//        if (UserConstants.NOT_UNIQUE.equals(settingService.checkSettingKeyUnique(setting)))
//        {
//            return error("新增参数'" + setting.getSettingName() + "'失败，参数键名已存在");
//        }
        setting.setCreateBy(SecurityUtils.getUsername());
        return toAjax(settingService.insertSetting(setting));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("okx:setting:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OkxSetting setting)
    {
        if (UserConstants.NOT_UNIQUE.equals(settingService.checkSettingKeyUnique(setting)))
        {
            return error("修改参数'" + setting.getSettingName() + "'失败，参数键名已存在");
        }
        setting.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(settingService.updateSetting(setting));
    }

    /**
     * 删除参数配置
     */
    @RequiresPermissions("okx:setting:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{settingIds}")
    public AjaxResult remove(@PathVariable Long[] settingIds)
    {
        settingService.deleteSettingByIds(settingIds);
        return success();
    }

    /**
     * 刷新参数缓存
     */
    @RequiresPermissions("okx:setting:remove")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        settingService.resetSettingCache();
        return success();
    }
}
