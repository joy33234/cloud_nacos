package com.seektop.fund.controller.backend;

import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.GlPaymentChannelBusiness;
import com.seektop.fund.business.recharge.GlFundPaymentRecommendAmountBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBankBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.PaymentRecommandAmountEditDo;
import com.seektop.fund.controller.backend.param.recharge.PaymentRecommandAmountListDo;
import com.seektop.fund.controller.backend.param.recharge.PaymentTypeEditParamDO;
import com.seektop.fund.controller.backend.param.recharge.PaymentTypeListParamDO;
import com.seektop.fund.handler.PaymentHandler;
import com.seektop.fund.model.GlPayment;
import com.seektop.fund.model.GlPaymentChannel;
import com.seektop.fund.model.GlWithdrawBank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/manage/fund/payment")
public class PaymentManageController extends FundBackendBaseController {

    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private GlPaymentChannelBusiness glPaymentChannelBusiness;
    @Resource
    private GlWithdrawBankBusiness glWithdrawBankBusiness;
    @Resource
    private GlFundPaymentRecommendAmountBusiness recommendAmountBusiness;
    @Resource
    private PaymentHandler paymentHandler;

    /**
     * 所有支付方式
     */
    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result paymentList(ManageParamBaseDO paramBaseDO) {
        List<GlPayment> paymentList =  glPaymentBusiness.findAll();
        paymentList.stream().forEach(item -> {
            item.setPaymentName(FundLanguageUtils.getPaymentName(item.getPaymentId(), item.getPaymentName(), paramBaseDO.getLanguage()));
        });
        return Result.genSuccessResult(paymentList);
    }

    /**
     * 所有渠道
     *
     * @return
     */
    @PostMapping(value = "/list/channel", produces = "application/json;charset=utf-8")
    public Result paymentChannelList(ManageParamBaseDO paramBaseDO) {
        List<GlPaymentChannel> channelList = glPaymentChannelBusiness.findValidPaymentChannel();
        return Result.genSuccessResult(channelList);
    }

    @PostMapping(value = "/list/bank", produces = "application/json;charset=utf-8")
    public Result bankList(ManageParamBaseDO paramBaseDO) {
        List<GlWithdrawBank> bankList = glWithdrawBankBusiness.findAll();
        bankList.stream().forEach(item -> {
            item.setBankName(item.getBankName());
        });
        return Result.genSuccessResult(glWithdrawBankBusiness.findAll());
    }

    /**
     * 查询所有出款商户
     * @return
     */
    @PostMapping(value = "/list/merchant", produces = "application/json;charset=utf-8")
    public Result listMerchant(ManageParamBaseDO paramBaseDO) {
        List<GlPaymentChannel> channelList = glPaymentChannelBusiness.findAll();
        return Result.genSuccessResult(glPaymentChannelBusiness.findAll());
    }


    /**
     * 查询所有支付方式推荐金额
     * @return
     */
    @PostMapping(value = "/list/amount", produces = "application/json;charset=utf-8")
    public Result listAmount(@Validated PaymentRecommandAmountListDo listDO) {
        return Result.genSuccessResult(recommendAmountBusiness.page(listDO));
    }


    /**
     * 更新支付方式推荐金额
     * @return
     */
    @PostMapping(value = "/update/amount", produces = "application/json;charset=utf-8")
    public Result updateAmount(@Validated PaymentRecommandAmountEditDo editDo, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        recommendAmountBusiness.update(editDo,admin.getUsername());
        return Result.genSuccessResult();
    }

    /**
     * 支付方式管理维护列表
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/maintain/list", produces = "application/json;charset=utf-8")
    public Result paymentMaintainList(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated PaymentTypeListParamDO paramDO) {
        return paymentHandler.list(admin, paramDO);
    }

    /**
     * 支付方式管理编辑保存
     *
     * @param admin
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/maintain/submit/edit", produces = "application/json;charset=utf-8")
    public Result paymentMaintainSubmitEdit(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin, @Validated PaymentTypeEditParamDO paramDO) {
        return paymentHandler.submitEdit(admin, paramDO);
    }

}