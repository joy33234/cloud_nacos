package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlPaymentMerchantAccountBusiness;
import com.seektop.fund.controller.backend.param.recharge.account.*;
import com.seektop.fund.handler.PaymentMerchantAppHandler;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


/**
 * 充值商户应用
 */
@Slf4j
@RestController
@RequestMapping("/manage/fund/payment/merchant")
public class PaymentMerchantAccountController extends FundBackendBaseController {

    @Resource
    private GlPaymentMerchantAccountBusiness merchantAccountBusiness;
    @Resource
    private PaymentMerchantAppHandler paymentMerchantAppHandler;

    /**
     * 充值通道是否显示开关配置
     */
    @PostMapping(value = "/setting", produces = "application/json;charset=utf-8")
    public Result showMerchantSetting(@RequestParam(defaultValue = "0") Integer status) {
        merchantAccountBusiness.merchantSetting(status);
        return Result.genSuccessResult();
    }

    /**
     * 查看充值通道是否显示开关配置
     *
     * @Param status 开关状态 0:开启  1:隐藏
     */
    @PostMapping(value = "/view", produces = "application/json;charset=utf-8")
    public Result showMerchantView() {
        return Result.genSuccessResult(merchantAccountBusiness.getMerchantSetting());
    }


    /**
     * 充值商户列表
     *
     * @param listDO
     * @return
     */
    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result listMerchantAccount(@Validated MerchantAccountListDO listDO) {
        return Result.genSuccessResult(merchantAccountBusiness.page(listDO));
    }

    /**
     * 新增充值商户
     *
     * @param addDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/add", produces = "application/json;charset=utf-8")
    public Result addMerchantAccount(@Validated MerchantAccountAddDO addDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

        merchantAccountBusiness.add(addDO, admin);

        return Result.genSuccessResult();

    }

    /**
     * 批量更新商户状态
     *
     * @param editDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/batch/update", produces = "application/json;charset=utf-8")
    public Result batchUpdateMerchantAccount(@Validated MerchantAccountBatchEditDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

        merchantAccountBusiness.batchUpdate(editDO, admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();

    }

    /**
     * 更新商户信息
     *
     * @param editDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update", produces = "application/json;charset=utf-8")
    public Result updateMerchantAccount(@Validated MerchantAccountEditDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

        merchantAccountBusiness.update(editDO,admin);

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
    public Result getMerchantScript(@RequestParam Integer merchantId) throws GlobalException {

        String script = merchantAccountBusiness.getScript(merchantId);

        return Result.genSuccessResult(script);
    }

    /**
     * 更新商户信息
     *
     * @param editDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update/script", produces = "application/json;charset=utf-8")
    public Result updateMerchantScript(@Validated MerchantAccountEditScriptDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

        merchantAccountBusiness.updateScript(editDO,admin);

        return Result.genSuccessResult();
    }

    /**
     * 删除商户
     *
     * @param merchantId
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/delete", produces = "application/json;charset=utf-8")
    public Result deleteMerchantAccount(@RequestParam Integer merchantId, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

       merchantAccountBusiness.delete(merchantId,admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();

    }

    /**
     * 商户成功率列表
     *
     * @param listDO
     * @return
     */
    @PostMapping(value = "/success/rate/list", produces = "application/json;charset=utf-8")
    public Result findSuccessRateList(MerchantAccountListDO listDO) {
        PageInfo<GlPaymentMerchantaccount> result = merchantAccountBusiness.findSuccessRateList(listDO);
        return Result.genSuccessResult(result);
    }


}
