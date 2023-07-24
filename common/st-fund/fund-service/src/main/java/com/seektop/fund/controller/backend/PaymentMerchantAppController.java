package com.seektop.fund.controller.backend;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.app.*;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.handler.PaymentMerchantAppHandler;
import com.seektop.fund.model.GlPaymentChannelBank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 充值商户应用
 */
@Slf4j
@RestController
@RequestMapping("/manage/fund/payment/merchant/app")
public class PaymentMerchantAppController extends FundBackendBaseController {

    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;
    @Autowired
    private PaymentMerchantAppHandler paymentMerchantAppHandler;

    /**
     * 商户应用列表
     *
     * @param listDO
     * @return
     */
    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result pageList(MerchantAccountAppQueryDO listDO) {
        return Result.genSuccessResult(glPaymentMerchantAppBusiness.pageList(listDO));
    }

    /**
     * 新增商户应用
     *
     * @param appAddDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/add", produces = "application/json;charset=utf-8")
    public Result addMerchantApp(@Validated MerchantAccountAppAddDO appAddDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        paymentMerchantAppHandler.save(appAddDO, admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();

    }

    /**
     * 更新商户应用
     *
     * @param merchantApp
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update", produces = "application/json;charset=utf-8")
    public Result edit(@Validated MerchantAccountAppEditDO merchantApp, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        paymentMerchantAppHandler.update(merchantApp, admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();
    }


    /**
     * 批量更新商户应用状态
     *
     * @param dto
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update/status", produces = "application/json;charset=utf-8")
    public Result updateMerchantStatus(MerchantAccountAppEditStatusDO dto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        glPaymentMerchantAppBusiness.updateStatus(dto, admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();
    }

    /**
     * 推荐商户应用
     *
     * @param recommendDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update/recommend", produces = "application/json;charset=utf-8")
    public Result updateMerchantRecommend(MerchantAccountAppEditRecommendDO recommendDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        glPaymentMerchantAppBusiness.updateRecommend(recommendDO, admin);
        return Result.genSuccessResult();
    }


    /**
     * 置顶商户应用
     *
     * @param topStatusDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/update/topping", produces = "application/json;charset=utf-8")
    public Result updateMerchantTopping(MerchantAccountAppEditTopStatusDO topStatusDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        glPaymentMerchantAppBusiness.updateTopping(topStatusDO, admin);
        return Result.genSuccessResult();
    }


    /**
     * 删除商户应用
     *
     * @param id
     * @param admin
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/delete", produces = "application/json;charset=utf-8")
    public Result deleteMerchant(@RequestParam Integer id, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        glPaymentMerchantAppBusiness.delete(id, admin);
        paymentMerchantAppHandler.updateAgencyLevel();
        return Result.genSuccessResult();
    }

    /**
     * 网银支付银行卡限额
     *
     * @param channelId
     * @return
     */
    @PostMapping(value = "/bank/list", produces = "application/json;charset=utf-8")
    public Result findBankList(@RequestParam Integer channelId) {
        return Result.genSuccessResult(glPaymentChannelBankBusiness.findList(channelId));
    }

}
