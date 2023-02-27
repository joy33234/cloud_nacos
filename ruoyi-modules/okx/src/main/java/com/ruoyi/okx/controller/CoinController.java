package com.ruoyi.okx.controller;

import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.okx.business.CoinBusiness;
import com.ruoyi.okx.domain.OkxCoin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/coin")
public class CoinController extends BaseController
{
    @Autowired
    private CoinBusiness coinBusiness;

    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:coin:list")
    @GetMapping("/list")
    public TableDataInfo list(OkxCoin coin){
        startPage();
        List<OkxCoin> list = coinBusiness.selectCoinList(coin);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("okx:coin:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, OkxCoin coin)
    {
        List<OkxCoin> list = coinBusiness.selectCoinList(coin);
        ExcelUtil<OkxCoin> util = new ExcelUtil<OkxCoin>(OkxCoin.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/{coin}")
    public AjaxResult getInfo(@PathVariable String coin)
    {
        return success(coinBusiness.findOne(coin));
    }


}
