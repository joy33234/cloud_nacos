package com.seektop.fund.configuration;

import com.seektop.constant.FundConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RandomAmountConfiguration {

    @Bean
    public List<Integer> randomAmountPaymentIds(){
        List<Integer> paymentIds = new ArrayList<>();
        paymentIds.add(FundConstant.PaymentType.BANKCARD_TRANSFER);
        paymentIds.add(FundConstant.PaymentType.ALI_TRANSFER);
        paymentIds.add(FundConstant.PaymentType.WECHAT_TRANSFER);
        paymentIds.add(FundConstant.PaymentType.UNION_TRANSFER);
        paymentIds.add(FundConstant.PaymentType.DIGITAL_PAY);
        paymentIds.add(FundConstant.PaymentType.RMB_PAY);
        return paymentIds;
    }

    @Bean
    public List<Integer> c2CPaymentIds(){
        List<Integer> paymentIds = new ArrayList<>();
        paymentIds.add(FundConstant.PaymentType.CTOC_BANK_PAY);
        paymentIds.add(FundConstant.PaymentType.CTOC_ALI_PAY);
        paymentIds.add(FundConstant.PaymentType.CTOC_WECHAT_PAY);
        paymentIds.add(FundConstant.PaymentType.CTOC_UNION_PAY);
        return paymentIds;
    }
}
