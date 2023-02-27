package com.ruoyi.okx.controller;

import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.okx.business.AccountBusiness;
import com.ruoyi.okx.business.StrategyBusiness;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.domain.OkxStrategy;
import com.ruoyi.okx.params.DO.OkxAccountDO;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import com.ruoyi.system.api.domain.SysRole;
import com.ruoyi.system.api.domain.SysUser;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/account")
public class AccountController extends BaseController
{
    @Autowired
    private AccountBusiness accountBusiness;

    @Autowired
    private StrategyBusiness strategyBusiness;

    @Autowired
    private SettingService settingService;
    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:account:list")
    @GetMapping("/list")
    public TableDataInfo list(OkxAccountDO account)
    {
        startPage();
        List<OkxAccount> list = accountBusiness.list(account);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("okx:account:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, OkxAccountDO account)
    {
        List<OkxAccount> list = accountBusiness.list(account);
        ExcelUtil<OkxAccount> util = new ExcelUtil<OkxAccount>(OkxAccount.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @GetMapping(value = "/{accountId}")
    public AjaxResult getInfo(@PathVariable Long accountId)
    {
        AjaxResult ajax = AjaxResult.success();
        List<OkxSetting> settingList = settingService.selectSettingList(new OkxSetting());
        settingList.stream().forEach(item -> item.setDesc(item.getSettingName() + "-" + item.getSettingValue()));
        ajax.put("settings", settingList);
        if (StringUtils.isNotNull(accountId)) {
            List<OkxStrategy> strategies = strategyBusiness.list(new OkxStrategy(accountId.intValue()));
            ajax.put(AjaxResult.DATA_TAG, accountBusiness.getById(accountId));
            ajax.put("settingIds", CollectionUtils.isEmpty(strategies) ? Lists.newArrayList() : Arrays.asList(strategies.get(0).getSettingIds().split(","))
                    .parallelStream()
                    .map(a -> Long.parseLong(a.trim()))
                    .collect(Collectors.toList()));
        }
        return ajax;
    }

    /**
     * 新增参数配置
     */
    @RequiresPermissions("okx:account:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OkxAccountDO accountDO)
    {
        if (UserConstants.NOT_UNIQUE.equals(accountBusiness.checkKeyUnique(accountDO)))
        {
            return error("新增参数'" + accountDO.getApikey() + "'失败，参数键名已存在");
        }
        return toAjax(accountBusiness.save(DtoUtils.transformBean(accountDO,OkxAccount.class)));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("okx:account:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OkxAccountDO accountDO)
    {
        if (UserConstants.NOT_UNIQUE.equals(accountBusiness.checkKeyUnique(accountDO)))
        {
            return error("修改参数'" + accountDO.getApikey() + "'失败，参数键名已存在");
        }
        return toAjax(accountBusiness.update(DtoUtils.transformBean(accountDO, OkxAccount.class)));
    }

    /**
     * 删除参数配置
     */
    @RequiresPermissions("okx:account:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{settingIds}")
    public AjaxResult remove(@PathVariable Long[] accountIds)
    {
        accountBusiness.removeBatchByIds(Arrays.asList(accountIds));
        return success();
    }

}
