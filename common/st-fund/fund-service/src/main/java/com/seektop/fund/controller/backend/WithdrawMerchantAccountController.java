package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawMerchantAccountBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.ValidMerchantAccountForm;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawMerchantAccountDO;
import com.seektop.fund.controller.backend.dto.withdraw.merchant.*;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/manage/fund/withdraw/merchant/account")
public class WithdrawMerchantAccountController extends FundBackendBaseController {

    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    /**
     * 出款商户列表
     */
    @PostMapping(value = "/page")
    public Result pageList(@Validated WithdrawMerchantAccountQueryDO queryDO) {
        PageInfo<GlWithdrawMerchantAccount> pageInfo = glWithdrawMerchantAccountBusiness.findMerchantAccountList(queryDO);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 有效的入款商户
     */
    @PostMapping(value = "/valid/list")
    public Result getValidMerchantAccountList(ValidMerchantAccountForm form) {
        List<WithdrawMerchantAccountDO> result = glWithdrawMerchantAccountBusiness.findValidMerchantAccount(form);
        return Result.newBuilder().success().addData(result).build();
    }

    /**
     * 提现商户列表
     */
    @PostMapping(value = "/all/list")
    public Result getAllMerchantAccountList() {
        List<GlWithdrawMerchantAccount> result = glWithdrawMerchantAccountBusiness.findAllMerchantaccount();
        return Result.newBuilder().success().addData(result).build();
    }

    /**
     * 新增提现商户
     */
    @PostMapping(value = "/add")
    public Result add(@Validated WithdrawMerchantAccountAddDO addDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.save(addDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 更新提现商户
     */
    @PostMapping(value = "/update", produces = "application/json;charset=utf-8")
    public Result update(@Validated WithdrawMerchantAccountEditDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.edit(editDO, adminDO);
        return Result.genSuccessResult();
    }


    /**
     * 获取商户脚本信息
     *
     * @param   merchantId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/get/script", produces = "application/json;charset=utf-8")
    public Result getScript(@RequestParam Integer merchantId) throws GlobalException {

        String script = glWithdrawMerchantAccountBusiness.getScript(merchantId);

        return Result.genSuccessResult(script);
    }

    /**
     * 更新提现商户脚本
     */
    @PostMapping(value = "/update/script", produces = "application/json;charset=utf-8")
    public Result updateScript(@Validated WithdrawMerchantAccountEditScriptDO editScriptDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.editAccountScript(editScriptDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 更新商户状态
     */
    @PostMapping(value = "/update/status", produces = "application/json;charset=utf-8")
    public Result updateStatus(@Validated WithdrawMerchantUpdateSatusDO editStatusDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.updateStatus(editStatusDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 更新商户开启状态
     */
    @PostMapping(value = "/update/open/status", produces = "application/json;charset=utf-8")
    public Result updateOpenStatus(@Validated WithdrawMerchantUpdateSatusDO editStatusDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.updateOpenStatus(editStatusDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 删除提现商户
     */
    @PostMapping(value = "/delete", produces = "application/json;charset=utf-8")
    public Result delete(@Validated WithdrawMerchantAccountDeleteDO deleteDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawMerchantAccountBusiness.delete(deleteDO, adminDO);
        return Result.genSuccessResult();
    }



    /**
     * 查询分单-三方出款商户列表
     */
    @PostMapping(value = "/seperator/list")
    public Result seperatorMerchantList(ParamBaseDO paramBaseDO) {

        return Result.genSuccessResult(glWithdrawMerchantAccountBusiness.findSeperatordMerchantAccount(paramBaseDO));
    }

    /**
     * 订单出款-三方出款商户列表
     */
    @PostMapping(value = "/order/list")
    public Result orderMerchantList(@Validated WithdrawMerchantSearchDO searchDO) throws GlobalException {
        return Result.genSuccessResult(glWithdrawMerchantAccountBusiness.findOrderMerchantAccount(searchDO));
    }


}
