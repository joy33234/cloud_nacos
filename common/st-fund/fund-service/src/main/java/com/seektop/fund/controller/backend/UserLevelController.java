package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.FundUserLeveLockDto;
import com.seektop.fund.controller.backend.dto.FundUserLockResult;
import com.seektop.fund.controller.backend.param.recharge.*;
import com.seektop.fund.controller.backend.result.FundUserLevelListResult;
import com.seektop.fund.group.CommonGroup;
import com.seektop.fund.group.UpdateGroup;
import com.seektop.fund.handler.*;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.vo.ManageParamBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 后台用户分层管理接口
 * 原地址：/gl/manage/user/level
 */
@RestController
@RequestMapping(value = "/manage/fund/user/level", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
public class UserLevelController extends FundBackendBaseController {

    @Autowired
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Autowired
    private UserLevelHandler userLevelHandler;
    @Autowired
    private GlFundUserLevelLockBusiness fundUserLevelLockBusiness;
    @Resource
    private RechargeFailureMonitorHandler rechargeFailureMonitorHandler;
    @Resource
    private RechargeBettingHandler rechargeBettingHandler;
    @Resource
    private RechargeSuccessMonitorHandler rechargeSuccessMonitorHandler;

    /**
     * 用户充值成功自动层级调整配置-保存
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/submit/first/recharge/config/create")
    public Result submitFirstRechargeConfigCreate(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated FirstRechargeLevelConfigCreateParamDO paramDO) {
        return rechargeSuccessMonitorHandler.submitCreateConfig(admin, paramDO);
    }

    /**
     * 用户充值成功自动层级调整配置-编辑
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/submit/first/recharge/config/edit")
    public Result submitFirstRechargeConfigEdit(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated FirstRechargeLevelConfigEditParamDO paramDO) {
        return rechargeSuccessMonitorHandler.submitEditConfig(admin, paramDO);
    }

    /**
     * 用户充值成功自动层级调整配置-更新状态
     *
     * @param admin
     * @return
     */
    @PostMapping("/submit/first/recharge/config/update/status")
    public Result submitFirstRechargeConfigUpdateStatus(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, Integer levelId) {
        return rechargeSuccessMonitorHandler.submitUpdateStatus(admin, levelId);
    }

    /**
     * 用户充值成功自动层级调整配置-删除
     *
     * @param admin
     * @param levelId
     * @return
     */
    @PostMapping("/submit/first/recharge/config/delete")
    public Result submitFirstRechargeConfigDelete(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, Integer levelId) {
        return rechargeSuccessMonitorHandler.submitDelete(admin, levelId);
    }

    /**
     * 用户充值成功自动层级调整配置-列表
     *
     * @param admin
     * @return
     */
    @PostMapping("/first/recharge/config/list")
    public Result firstRechargeConfigList(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) {
        return rechargeSuccessMonitorHandler.configList(admin);
    }

    /**
     * 充值流水配置-列表
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/recharge/betting/config/list")
    public Result configList(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated RechargeBettingLevelConfigListParamDO paramDO) {
        return rechargeBettingHandler.configList(admin, paramDO);
    }

    /**
     * 充值流水配置-新增
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/recharge/betting/submit/create")
    public Result submitCreate(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated RechargeBettingLevelConfigCreateParamDO paramDO) {
        return rechargeBettingHandler.submitCreate(admin, paramDO);
    }

    /**
     * 充值流水配置-编辑
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/recharge/betting/submit/edit")
    public Result submitEdit(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated RechargeBettingLevelConfigEditParamDO paramDO) {
        return rechargeBettingHandler.submitEdit(admin, paramDO);
    }

    /**
     * 充值流水配置-开启
     *
     * @param admin
     * @param recordId
     * @return
     */
    @PostMapping("/recharge/betting/submit/open")
    public Result submitOpen(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, Long recordId) {
        return rechargeBettingHandler.submitOpen(admin, recordId);
    }

    /**
     * 充值流水配置-关闭
     *
     * @param admin
     * @param recordId
     * @return
     */
    @PostMapping("/recharge/betting/submit/close")
    public Result submitClose(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, Long recordId) {
        return rechargeBettingHandler.submitClose(admin, recordId);
    }

    /**
     * 充值流水配置-删除
     *
     * @param admin
     * @param recordId
     * @return
     */
    @PostMapping("/recharge/betting/submit/delete")
    public Result submitDelete(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, Long recordId) {
        return rechargeBettingHandler.submitDelete(admin, recordId);
    }

    /**
     * 连续充值失败配置列表
     *
     * @param admin
     * @return
     */
    @PostMapping("/recharge/failure/config/list")
    public Result configList(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) {
        return rechargeFailureMonitorHandler.configList(admin);
    }

    /**
     * 保存充值连续失败配置
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping("/submit/recharge/failure/config")
    public Result submitRechargeFailureConfig(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated RechargeFailureLevelConfigParamDO paramDO) {
        return rechargeFailureMonitorHandler.submitRechargeFailureConfig(admin, paramDO);
    }

    /**
     * 移除充值连续失败配置
     *
     * @param admin
     * @param levelIds
     * @return
     */
    @PostMapping("/submit/recharge/failure/remove")
    public Result submitRemoveConfig(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, String levelIds) {
        return rechargeFailureMonitorHandler.submitRemoveConfig(admin, levelIds);
    }

    /**
     * 设置连续充值失败自动变更层级(开关设置)
     *
     * @param isOpen
     * @return
     */
    @PostMapping("/submit/recharge/failure/switch")
    public Result setSwitch(@RequestParam(required = false, name = "isOpen") Integer isOpen) {
        return rechargeFailureMonitorHandler.setSwitch(isOpen);
    }

    /**
     * 设置连续充值失败自动变更层级(开关设置)
     *
     * @return
     */
    @PostMapping("/get/recharge/failure/switch")
    public Result setSwitch() {
        return rechargeFailureMonitorHandler.getSwitch();
    }

    /**
     * 查询所有用户层级
     * @param levelType 层级类型 : -1 所有,0 会员,1 代理
     * @return
     */
    @RequestMapping("/list")
    public Result list(@RequestParam(required = false, defaultValue = "-1", name = "levelType") Integer levelType, ManageParamBase paramBase) {
        return Result.genSuccessResult(fundUserlevelBusiness.findByLevelType(levelType));
    }

    /**
     * 创建用户层级
     * @param fundUserlevel
     * @param admin
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/add")
    public Result add(@Validated(value = CommonGroup.class) GlFundUserlevel fundUserlevel,
                      @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        fundUserlevelBusiness.save(fundUserlevel, admin);
        return Result.genSuccessResult();
    }

    /**
     * 修改用户层级
     * @param fundUserlevel
     * @param admin
     * @return
     */
    @RequestMapping("/update")
    public Result update(@Validated(value = {UpdateGroup.class, CommonGroup.class}) GlFundUserlevel fundUserlevel,
                         @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        fundUserlevelBusiness.update(fundUserlevel, admin);
        return Result.genSuccessResult();
    }

    /**
     * 删除用户层级
     * @param levelId
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/delete")
    public Result delete(@RequestParam Integer levelId) throws GlobalException {
        userLevelHandler.delete(levelId);
        return Result.genSuccessResult();
    }

    /**
     * 查询用户所在层级
     * @param username
     * @return
     */
    @RequestMapping("/detail")
    public Result detail(@RequestParam String username) throws GlobalException {
        GlFundUserLevelLock lock = fundUserLevelLockBusiness.findByUsername(username);
        return Result.genSuccessResult(lock);
    }

    /**
     * 查询层级锁定会员
     * @param levelId
     * @return
     */
    @RequestMapping("/detail/lock")
    public Result detailLock(@RequestParam Integer levelId) {
        return Result.genSuccessResult(fundUserLevelLockBusiness.findLockUser(levelId));
    }

    /**
     * 锁定会员
     * @param lockDto
     * @param admin
     * @return
     */
    @RequestMapping("/lock")
    public Result lock(@Validated FundUserLeveLockDto lockDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        FundUserLockResult result = userLevelHandler.lock(lockDto, admin);
        return Result.genSuccessResult(result);
    }

    /**
     * 解锁会员
     * @param username
     * @return
     */
    @RequestMapping("/unlock")
    public Result unlock(@RequestParam List<String> username) throws GlobalException {
        fundUserLevelLockBusiness.unlock(username);
        return Result.genSuccessResult();
    }
}
