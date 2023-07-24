package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawAutoConditionBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConditionBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.condition.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@Slf4j
@RestController
@RequestMapping("/manage/fund/withdraw/condition")
public class WithdrawConditionController extends FundBackendBaseController {

    @Resource
    private GlWithdrawConditionBusiness business;

    @Resource
    private GlWithdrawAutoConditionBusiness glWithdrawAutoConditionBusiness;


    /**
     * 分单设置列表查询（不做分页）
     *
     * @param condtionDto
     * @return
     */
    @PostMapping(value = "/list")
    public Result list(WithdrawCondtionQueryDO condtionDto) {
        return Result.genSuccessResult(business.list(condtionDto));
    }


    /**
     * 保存分单设置
     *
     * @param adminDO
     * @param condition
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/save")
    public Result saveWithdrawCondition(@Validated WithdrawConditionAddDO condition, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        business.save(condition, adminDO);
        return Result.genSuccessResult();

    }

    /**
     * 更新分单设置
     *
     * @param adminDO
     * @param condition
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update")
    public Result updateWithdrawCondition(@Validated WithdrawConditionEditDO condition, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        business.update(condition, adminDO);
        return Result.genSuccessResult();

    }

    /**
     * 删除分单设置
     *
     * @param adminDO
     * @param deleteDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/delete")
    public Result deleteWithdrawCondition(@Validated WithdrawConditionDeleteDO deleteDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        business.delete(deleteDO, adminDO);
        return Result.genSuccessResult();
    }


    /**
     * 自动出款条件列表
     *
     * @param queryDO
     * @return
     */
    @PostMapping(value = "/auto/list")
    public Result withdrawAutoConditionList(WithdrawAutoCondtionQueryDO queryDO) {
        return Result.genSuccessResult(glWithdrawAutoConditionBusiness.getWithdrawAutoConditionList(queryDO));
    }

    /**
     * 保存自动出款条件
     *
     * @param addDO
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/auto/save")
    public Result saveWithdrawAutoCondition(@Validated WithdrawAutoConditionAddDO addDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawAutoConditionBusiness.save(addDO, adminDO);
        return Result.genSuccessResult();
    }


    /**
     * 编辑自动出款条件
     *
     * @param editDO
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/auto/update")
    public Result updateWithdrawAutoCondition(@Validated WithdrawAutoConditionEditDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawAutoConditionBusiness.update(editDO, adminDO);
        return Result.genSuccessResult();
    }


    @PostMapping(value = "/auto/delete")
    public Result deleteWithdrawAutoCondition(@Validated WithdrawAutoConditionDeleteDO deleteDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawAutoConditionBusiness.delete(deleteDO, adminDO);
        return Result.genSuccessResult();
    }

}
