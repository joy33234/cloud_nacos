//package com.ruoyi.okx.controller;
//
//import com.baomidou.mybatisplus.core.toolkit.Wrappers;
//import com.ruoyi.common.core.constant.UserConstants;
//import com.ruoyi.common.core.utils.poi.ExcelUtil;
//import com.ruoyi.common.core.web.controller.BaseController;
//import com.ruoyi.common.core.web.domain.AjaxResult;
//import com.ruoyi.common.core.web.page.TableDataInfo;
//import com.ruoyi.common.log.annotation.Log;
//import com.ruoyi.common.log.enums.BusinessType;
//import com.ruoyi.common.security.annotation.RequiresPermissions;
//import com.ruoyi.okx.business.SellStrategyBusiness;
//import com.ruoyi.okx.domain.OkxSellStrategy;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.validation.annotation.Validated;
//import org.springframework.web.bind.annotation.*;
//
//import javax.servlet.http.HttpServletResponse;
//import java.util.Arrays;
//import java.util.List;
//
///**
// * 参数配置 信息操作处理
// *
// * @author ruoyi
// */
//@RestController
//@RequestMapping("/sell/strategy")
//public class SellStrategyController extends BaseController
//{
//    @Autowired
//    private SellStrategyBusiness business;
//
//    /**
//     * 获取参数配置列表
//     */
//    @RequiresPermissions("okx:sellStrategy:list")
//    @GetMapping("/list")
//    public TableDataInfo list(OkxSellStrategy strategy)
//    {
//        startPage();
//        List<OkxSellStrategy> list = business.list(strategy);
//        return getDataTable(list);
//    }
//
//    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
//    @RequiresPermissions("okx:sellStrategy:export")
//    @PostMapping("/export")
//    public void export(HttpServletResponse response, OkxSellStrategy account)
//    {
//        List<OkxSellStrategy> list = business.list(account);
//        ExcelUtil<OkxSellStrategy> util = new ExcelUtil<OkxSellStrategy>(OkxSellStrategy.class);
//        util.exportExcel(response, list, "参数数据");
//    }
//
//    /**
//     * 根据参数编号获取详细信息
//     */
//    @GetMapping(value = "/{id}")
//    public AjaxResult getInfo(@PathVariable Long accountId){
//        return success(business.getById(accountId));
//    }
//
////    /**
////     * 根据参数键名查询参数值
////     */
////    @GetMapping(value = "/settingKey/{settingKey}")
////    public AjaxResult getSettingKey(@PathVariable String settingKey)
////    {
////        return success(settingService.selectSettingByKey(settingKey));
////    }
//
//    /**
//     * 新增参数配置
//     */
//    @RequiresPermissions("okx:sellStrategy:add")
//    @Log(title = "参数管理", businessType = BusinessType.INSERT)
//    @PostMapping
//    public AjaxResult add(@Validated @RequestBody OkxSellStrategy strategy)
//    {
//        if (UserConstants.NOT_UNIQUE.equals(business.checkKeyUnique(strategy)))
//        {
//            return error("新增参数'" + strategy.getName() + "'失败，参数键名已存在");
//        }
//        return toAjax(business.save(strategy));
//    }
//
//    /**
//     * 修改参数配置
//     */
//    @RequiresPermissions("okx:sellStrategy:edit")
//    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
//    @PutMapping
//    public AjaxResult edit(@Validated @RequestBody OkxSellStrategy strategy)
//    {
//        if (UserConstants.NOT_UNIQUE.equals(business.checkKeyUnique(strategy))){
//            return error("修改参数'" + strategy.getName() + "'失败，参数键名已存在");
//        }
//        return toAjax(business.update(strategy, Wrappers.emptyWrapper()));
//    }
//
//    /**
//     * 删除参数配置
//     */
//    @RequiresPermissions("okx:sellStrategy:remove")
//    @Log(title = "参数管理", businessType = BusinessType.DELETE)
//    @DeleteMapping("/{ids}")
//    public AjaxResult remove(@PathVariable Long[] accountIds)
//    {
//        business.removeBatchByIds(Arrays.asList(accountIds));
//        return success();
//    }
//
//}
