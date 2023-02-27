package com.ruoyi.okx.controller;

import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.okx.business.BuyRecordBusiness;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.params.DO.BuyRecordDO;
import com.ruoyi.okx.utils.DtoUtils;
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
@RequestMapping("/buy")
public class BuyRecordController extends BaseController
{
    @Autowired
    private BuyRecordBusiness buyRecordBusiness;

    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:buy:list")
    @GetMapping("/list")
    public TableDataInfo list(BuyRecordDO buyRecordDO)
    {
        startPage();
        List<OkxBuyRecord> list = buyRecordBusiness.selectList(buyRecordDO);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("okx:buy:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, BuyRecordDO buyRecordDO)
    {
        List<OkxBuyRecord> list = buyRecordBusiness.selectList(buyRecordDO);
        ExcelUtil<OkxBuyRecord> util = new ExcelUtil<OkxBuyRecord>(OkxBuyRecord.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(buyRecordBusiness.getById(id));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("okx:account:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody BuyRecordDO buyRecordDO)
    {
        return toAjax(buyRecordBusiness.update(DtoUtils.transformBean(buyRecordDO, OkxBuyRecord.class)));
    }

}
