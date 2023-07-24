package com.seektop.fund.handler;

import com.github.pagehelper.PageInfo;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.PaymentTypeEditParamDO;
import com.seektop.fund.controller.backend.param.recharge.PaymentTypeListParamDO;
import com.seektop.fund.model.GlPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PaymentHandler {

    private final GlPaymentBusiness glPaymentBusiness;

    public Result list(GlAdminDO adminDO, PaymentTypeListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            PageInfo<GlPayment> pageInfo = glPaymentBusiness.findList(paramDO.getPage(), paramDO.getSize(), paramDO.getCoin());
            List<GlPayment> paymentList = pageInfo.getList();
            if (CollectionUtils.isEmpty(paymentList)) {
                return newBuilder.success().addData(pageInfo).build();
            }
            for (GlPayment glPayment : paymentList) {
                glPayment.setPaymentName(FundLanguageUtils.getPaymentName(glPayment.getPaymentId(), glPayment.getPaymentName(), paramDO.getLanguage()));
            }
            pageInfo.setList(paymentList);
            return newBuilder.success().addData(pageInfo).build();
        } catch (Exception ex) {
            log.error("查询支付方式列表发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public Result submitEdit(GlAdminDO adminDO, PaymentTypeEditParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            GlPayment glPayment = glPaymentBusiness.findById(paramDO.getPaymentId());
            if (ObjectUtils.isEmpty(glPayment)) {
                return newBuilder.fail(ResultCode.DATA_ERROR).build();
            }
            GlPayment updatePayment = new GlPayment();
            updatePayment.setPaymentId(paramDO.getPaymentId());
            updatePayment.setPaymentLogo(paramDO.getPaymentLogo());
            updatePayment.setCoin(paramDO.getCoin());
            glPaymentBusiness.updateByPrimaryKeySelective(updatePayment);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("编辑支付方式发生异常", ex);
            return newBuilder.fail().build();
        }
    }

}