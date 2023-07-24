package com.seektop.fund.controller.backend;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlPaymentMerchantFeeBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeCommonAmountDO;
import com.seektop.fund.controller.backend.param.recharge.fee.ChannelBankEditDO;
import com.seektop.fund.controller.backend.param.recharge.fee.MerchantFeeEditDO;
import com.seektop.fund.controller.backend.param.recharge.fee.MerchantFeeListDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.model.GlPaymentMerchantFee;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 充值商户金额设置 (充值通用设置)
 */
@Slf4j
@RestController
@RequestMapping("/manage/fund/payment/merchant/fee")
public class PaymentMerchantFeeController extends FundBackendBaseController {

    @Resource
    private GlPaymentMerchantFeeBusiness glPaymentMerchantFeeBusiness;

    @Resource
    private RedisService redisService;

    /**
     * 获取金额设置
     *
     * @param listDO
     * @return throws GlobalException
     */
    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result listFee(@Validated MerchantFeeListDO listDO) throws GlobalException {
        Map<String, Object> map = glPaymentMerchantFeeBusiness.listFee(listDO);
        for (Map.Entry<String, Object> entry:map.entrySet()) {
            if (entry.getKey().equals("data") && !ObjectUtils.isEmpty(entry.getValue())) {
                List<GlPaymentMerchantFee> feeList = (List<GlPaymentMerchantFee>) entry.getValue();
                feeList.stream().forEach(item -> {
                    item.setPaymentName(FundLanguageUtils.getPaymentName(item.getPaymentId(), item.getPaymentName(), listDO.getLanguage()));
                });
            }
        }
        return Result.genSuccessResult(glPaymentMerchantFeeBusiness.listFee(listDO));

    }

    /**
     * 更新通用金额
     *
     * @param commonAmountDO
     * @return
     */
    @PostMapping(value = "/update/fastAmount", produces = "application/json;charset=utf-8")
    public Result updateFastAmount(@Validated RechargeCommonAmountDO commonAmountDO) throws GlobalException {
        if (commonAmountDO.getLimitType() != FundConstant.LimitType.PROXY
                && commonAmountDO.getMaxAmount().compareTo(commonAmountDO.getMinAmount()) != 1) {
            throw new GlobalException("最高金额必须大于最低金额");
        }
        if (commonAmountDO.getLimitType() != FundConstant.LimitType.PROXY
            && (ObjectUtils.isEmpty(commonAmountDO.getMinAmount()) || ObjectUtils.isEmpty(commonAmountDO.getMaxAmount()))) {
            throw new GlobalException("金额区间未配置");
        }
        if (commonAmountDO.getLimitType() != FundConstant.LimitType.PROXY
                && (commonAmountDO.getMinAmount().compareTo(BigDecimal.valueOf(100)) < 0
                    || commonAmountDO.getMaxAmount().compareTo(BigDecimal.valueOf(100)) < 0)) {
            throw new GlobalException("金额区间最低金额为100");
        }
        glPaymentMerchantFeeBusiness.updateAmount(commonAmountDO);

        return Result.genSuccessResult("设置成功");

    }



    /**
     * 更新充值金额
     *
     * @param editDO
     * @return
     */
    @PostMapping(value = "/update", produces = "application/json;charset=utf-8")
    public Result updateFee(@Validated MerchantFeeEditDO editDO, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {

        glPaymentMerchantFeeBusiness.update(editDO, admin);

        return Result.genSuccessResult();
    }

    /**
     * 网银支付-银行卡限额
     *
     * @param editDO
     * @return
     */
    @PostMapping(value = "/update/bank/limit", produces = "application/json;charset=utf-8")
    public Result updateBankLimit(@Validated ChannelBankEditDO editDO, @ModelAttribute(value = "adminInfo", binding =false) GlAdminDO admin) throws GlobalException {

        glPaymentMerchantFeeBusiness.updateBankLimit(editDO, admin);

        return Result.genSuccessResult();
    }

    /**
     * 设置充值轮询时间(单位：分钟)
     *
     * @param cycleTime
     * @return
     */
    @PostMapping(value = "set/cycleTime", produces = "application/json;charset=utf-8")
    public Result setCycleTime(@RequestParam Integer cycleTime) {
        if (cycleTime > 1440) {
            return Result.genFailResult("轮询时间不能越过一天");
        }
        redisService.set(RedisKeyHelper.RECHARGE_CYCLE_TIME, cycleTime, -1 );
        return Result.genSuccessResult();
    }

    /**
     * 获取充值轮询时间(单位：分钟)
     *
     * @return
     */
    @PostMapping(value = "get/cycleTime", produces = "application/json;charset=utf-8")
    public Result getCycleTime() {
        return Result.genSuccessResult(redisService.get(RedisKeyHelper.RECHARGE_CYCLE_TIME));
    }
}
