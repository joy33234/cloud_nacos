package com.ruoyi.okx.controller;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.okx.OkxApplication;
import com.ruoyi.okx.business.AccountBusiness;
import com.ruoyi.okx.business.CoinProfitBusiness;
import com.ruoyi.okx.business.ProfitBusiness;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.domain.OkxCoinProfit;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.params.DO.OkxAccountDO;
import com.ruoyi.okx.params.DO.OkxAccountEditDO;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import com.ruoyi.rabbitmq.ProducerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/account")
@Slf4j
public class AccountController extends BaseController
{
    @Autowired
    private AccountBusiness accountBusiness;

    @Autowired
    private SettingService settingService;

    @Resource
    private ProfitBusiness profitBusiness;

    @Resource
    private CoinProfitBusiness coinProfitBusiness;

    @Resource(name = "coinProducerService")
    private ProducerService producerService;

    @GetMapping("/test")
    public AjaxResult list2() throws Exception
    {

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(OkxApplication.class);
        // ApplicationContext applicationContext = new ClassPathXmlApplicationContext("beans.xml");



        // 打印 IOC 容器中所有 bean 的名称
        String[] namesForType = applicationContext.getBeanDefinitionNames();
        for (String name : namesForType) {
            System.out.println(name);
        }


        AjaxResult ajax = AjaxResult.success();


        OkxCoin coin = new OkxCoin();
        coin.setCoin("BTC");

        producerService.send(coin);
        ajax.put("coin",coin);
        return ajax;
    }


    /**
     * 获取参数配置列表
     */
    @RequiresPermissions("okx:account:list")
    @GetMapping("/list")
    public TableDataInfo list(OkxAccountDO account)
    {
        OkxCoin coin = new OkxCoin();
        coin.setCoin("BTC");
        producerService.send(coin);

        startPage();
        List<OkxAccount> list = accountBusiness.list(account);
        list.stream().forEach(item -> {
            item.setPassword(item.getPassword().substring(0,8) + "....");
            item.setSecretkey(item.getSecretkey().substring(0,8) + "....");
            item.setApikey(item.getApikey().substring(0,8) + "....");
        });
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
        List<OkxSetting> settingList = settingService.selectSettingList(new OkxSetting()).stream().sorted(Comparator.comparing(OkxSetting::getSettingKey)).collect(Collectors.toList());
        settingList.stream().forEach(item -> item.setDesc(item.getSettingName() + "-" + item.getSettingValue()));
        ajax.put("settings", settingList);
        if (ObjectUtils.isNotEmpty(accountId)) {
            OkxAccount okxAccount = accountBusiness.getById(accountId);
            okxAccount.setPassword(okxAccount.getPassword().substring(0,8) + "....");
            okxAccount.setSecretkey(okxAccount.getSecretkey().substring(0,8) + "....");
            okxAccount.setApikey(okxAccount.getApikey().substring(0,8) + "....");

            ajax.put(AjaxResult.DATA_TAG, okxAccount);
            ajax.put("settingIds", StringUtils.isEmpty(okxAccount.getSettingIds()) ? Lists.newArrayList() : Arrays.asList(okxAccount.getSettingIds().split(","))
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
        if (UserConstants.NOT_UNIQUE.equals(accountBusiness.checkKeyUnique(accountDO))){
            return error("新增参数'" + accountDO.getApikey() + "'失败，参数键名已存在");
        }
        if (!settingService.checkSettingKey(accountDO.getSettingIds())) {
            return error("配置策略失败，参数键名不唯一");
        }

        List<OkxSetting> okxSettingList = settingService.selectSettingByIds(accountDO.getSettingIds());
        List<OkxSetting> percentSettingList = okxSettingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_PERCENT)).collect(Collectors.toList());
        List<OkxSetting> amountSettingList = okxSettingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_AMOUNT)).collect(Collectors.toList());
        if (percentSettingList.size() != amountSettingList.size()) {
            return error("配置策略失败，参数键名不唯一");
        }
        OkxAccount okxAccount = DtoUtils.transformBean(accountDO, OkxAccount.class);
        okxAccount.setSettingIds(StringUtils.join(accountDO.getSettingIds(),","));
        okxAccount.setCreateTime(new Date());
        okxAccount.setCreateBy(SecurityUtils.getUsername());
        return toAjax(accountBusiness.save(okxAccount));
    }

    /**
     * 修改参数配置
     */
    @RequiresPermissions("okx:account:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OkxAccountEditDO accountDO)
    {
        if (!settingService.checkSettingKey(accountDO.getSettingIds())) {
            return error("配置策略失败，参数键名异常");
        }

        List<OkxSetting> okxSettingList = settingService.selectSettingByIds(accountDO.getSettingIds());
        List<OkxSetting> percentSettingList = okxSettingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_PERCENT)).collect(Collectors.toList());
        List<OkxSetting> amountSettingList = okxSettingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_AMOUNT)).collect(Collectors.toList());
        if (percentSettingList.size() != amountSettingList.size()) {
            return error("配置策略失败，参数键名不唯一");
        }
        System.out.println("accountDo:"  +  JSON.toJSONString(accountDO));
        OkxAccount okxAccount = DtoUtils.transformBean(accountDO, OkxAccount.class);
        okxAccount.setSettingIds(StringUtils.join(accountDO.getSettingIds(),","));
        okxAccount.setUpdateBy(SecurityUtils.getUsername());
        okxAccount.setUpdateTime(new Date());
        return toAjax(accountBusiness.update(okxAccount));
    }

    @RequiresPermissions("okx:account:changeStatus")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping("changeStatus")
    public AjaxResult changeStatus(@Validated @RequestBody OkxAccountDO accountDO)
    {
        OkxAccount okxAccount = accountBusiness.findOne(accountDO.getId());
        okxAccount.setStatus(accountDO.getStatus());
        return toAjax(accountBusiness.update(okxAccount));
    }

    /**
     * 删除参数配置
     */
    @RequiresPermissions("okx:account:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{accountId}")
    public AjaxResult initRiseDto(@PathVariable Integer accountId)
    {
        return toAjax(accountBusiness.initRiseDto(accountId));
    }

    /**
     * 删除参数配置
     */
    @GetMapping("profit")
    public AjaxResult profit(Integer accountId){
        return success(profitBusiness.profit(accountId));
    }

    /**
     * 删除参数配置
     */
    @GetMapping("profit/detail")
    public TableDataInfo detail(OkxCoinProfitDo profitDo)
    {
        startPage();
        List<OkxCoinProfit> list = coinProfitBusiness.selectList(profitDo);
        return getDataTable(list);
    }


}
