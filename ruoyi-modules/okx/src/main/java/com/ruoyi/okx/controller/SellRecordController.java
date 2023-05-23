package com.ruoyi.okx.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.okx.business.SellRecordBusiness;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxSellRecord;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.params.DO.BuyRecordDO;
import com.ruoyi.okx.params.DO.SellRecordDO;
import com.ruoyi.okx.utils.DtoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 卖出记录
 * 
 */
@RestController
@RequestMapping("/sell")
public class SellRecordController extends BaseController
{
    @Autowired
    private SellRecordBusiness sellRecordBusiness;

    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:sell:list")
    @GetMapping("/list")
    public TableDataInfo list(SellRecordDO sellRecordDO)
    {
        startPage();
        List<OkxSellRecord> list = sellRecordBusiness.selectList(sellRecordDO);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("okx:sell:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SellRecordDO sellRecordDO)
    {
        List<OkxSellRecord> list = sellRecordBusiness.selectList(sellRecordDO);
        ExcelUtil<OkxSellRecord> util = new ExcelUtil<OkxSellRecord>(OkxSellRecord.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(sellRecordBusiness.getById(id));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("okx:account:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SellRecordDO sellRecordDO)
    {
        return toAjax(sellRecordBusiness.update(DtoUtils.transformBean(sellRecordDO, OkxSellRecord.class)));
    }

}
