package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestDO;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawResult;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.payment.WithdrawNotify;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/manage/fund/withdraw")
public class WithdrawManageController extends FundBackendBaseController {

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource(name = "withdrawHandler")
    private WithdrawHandler withdrawHandler;

    /**
     * 三方手动出款和人工出款
     *
     * @param admin
     * @return
     */
    @PostMapping(value = "/approve", produces = "application/json;charset=utf-8")
    public Result approve(@Validated WithdrawApproveDO dto, @ModelAttribute("adminInfo") GlAdminDO admin) throws GlobalException {
        glWithdrawBusiness.approveWithdraw(dto, admin);
        return Result.genSuccessResult();
    }

    /**
     * 更新出款标签
     *
     * @param orderId
     * @param tag
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/tag/update", produces = "application/json;charset=utf-8")
    public Result updateTag(@RequestParam String orderId, @RequestParam String tag) throws GlobalException {
        glWithdrawBusiness.updateTag(orderId, tag);
        return Result.genSuccessResult();
    }

    /**
     * 申请提现退回
     *
     * @param requestDO
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/save/return", produces = "application/json;charset=utf-8")
    public Result saveReturn(@Validated WithdrawRequestDO requestDO,
                             @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawBusiness.requestWithdrawReturn(requestDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 申请强制成功
     *
     * @param requestDO
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/save/force/success", produces = "application/json;charset=utf-8")
    public Result saveForceSuccess(@Validated WithdrawRequestDO requestDO,
                                   @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawBusiness.requestWithdrawSuccess(requestDO, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 获取提现退回申请人列表
     *
     * @return
     */
    @PostMapping(value = "/list/return/creator", produces = "application/json;charset=utf-8")
    public Result listReturnCreator() {
        return Result.genSuccessResult(glWithdrawBusiness.getAllReturnCreator());
    }

    /**
     * 获取提现退回审核人列表
     *
     * @return
     */
    @PostMapping(value = "/list/return/approver", produces = "application/json;charset=utf-8")
    public Result listReturnApprover() {
        return Result.genSuccessResult(glWithdrawBusiness.getAllReturnApprover());
    }


    /**
     * 审核提现退回、提现成功
     *
     * @param approveDO
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/approve/return", produces = "application/json;charset=utf-8")
    public Result approveReturn(@Validated WithdrawRequestApproveDO approveDO,
                                @ModelAttribute("adminInfo") GlAdminDO adminDO) throws GlobalException {
        glWithdrawBusiness.doWithdrawRequestApprove(approveDO, adminDO);
        return Result.genSuccessResult();
    }


    /**
     * 查询出款商户余额
     *
     * @param merchantId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/account/balance")
    public Result accountBalance(@RequestParam Integer merchantId) throws GlobalException {
        return Result.genSuccessResult(glWithdrawBusiness.queryAccountBalance(merchantId));
    }

    /**
     * 修改提现订单备注信息
     *
     * @param requestDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update/remark")
    public Result updateRemark(@Validated WithdrawRequestDO requestDO) throws GlobalException {
        glWithdrawBusiness.updateWithdrawRemark(requestDO);
        return Result.genSuccessResult();
    }


    /**
     * 三方出款订单查询商户后台状态
     */
    @PostMapping(value = "/status/confirm", produces = "application/json;charset=utf-8")
    public Result withdrawStatusConfirm(@RequestParam String orderId) throws GlobalException {
        try {
            WithdrawNotify notify = withdrawHandler.withdrawStatusConfirm(orderId);
            return Result.genSuccessResult(notify);
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("withdrawHandler.withdrawStatusConfirm error", e);
            return Result.genFailResult("查询异常，请稍后再试");
        }
    }

    /**
     * 获取提现银行卡维护列表
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/bank/setting/info", produces = "application/json;charset=utf-8")
    public Result bankSettingInfo() throws GlobalException {
        return Result.genSuccessResult(glWithdrawBusiness.getBankSettingInfo());
    }

    /**
     * 保存提现银行卡维护列表
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/bank/setting/save", produces = "application/json;charset=utf-8")
    public Result bankSettingSave(@RequestParam List<Integer> bankIds, @RequestParam() String coin) throws GlobalException {
        glWithdrawBusiness.saveBankSetting(bankIds);
        return Result.genSuccessResult();
    }

    @PostMapping(value = "/detail", produces = "application/json;charset=utf-8")
    public Result WithdrawDetail(@RequestParam String orderId) throws GlobalException {
        GlWithdrawResult result = glWithdrawBusiness.getWithdrawDetali(orderId);
        return Result.genSuccessResult(result);
    }


    /**
     * 风控提现待审核数量
     * @return
     */
    @PostMapping("/approve/count")
    public Result listOperationTips() {
        return Result.genSuccessResult(glWithdrawBusiness.approveCount());
    }

}
