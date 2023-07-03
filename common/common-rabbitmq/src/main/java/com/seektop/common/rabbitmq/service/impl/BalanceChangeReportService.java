package com.seektop.common.rabbitmq.service.impl;

import com.seektop.common.rabbitmq.producer.ReportProducer;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.MsgEnum;
import com.seektop.enumerate.ReportEvent;
import com.seektop.enumerate.fund.WithdrawStatusEnum;
import com.seektop.report.fund.BalanceChangeReport;
import com.seektop.report.fund.RechargeReport;
import com.seektop.report.fund.WithdrawReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class BalanceChangeReportService {
    @Resource
    private ReportProducer reportProducer;

    public void report(WithdrawReport data) {
        try {
            if(data == null || data.getStatus() != WithdrawStatusEnum.PENDING){
                //没有发生账变
                return;
            }
            com.seektop.report.fund.BalanceChangeReport report = new com.seektop.report.fund.BalanceChangeReport();
            report.setUuid((String) data.get("uuid"));
            report.setUid((Integer) data.get("uid"));
            report.setAmount(data.getAmount());
            report.setBalanceBefore(data.getBalanceBefore());
            report.setBalanceAfter(data.getBalanceAfter());
            report.setFee(data.getFee());
            report.setReallyAmount(data.getAmountNet());
            report.setOrderType(MsgEnum.Withdraw.value());
            report.setTimestamp(new Date());
            reportProducer.sendMessage(ReportEvent.BalanceChange, report);
        }catch (Exception e){
            log.error("report balance change {}", data);
            log.error(e.getMessage(), e);
        }
    }

    public void report(RechargeReport data) {
        try {
            //未成功的充值没有账变
            if(data.getStatus() == null || data.getStatus().value() != ProjectConstant.RechargeStatus.SUCCESS ){
                return;
            }
            BalanceChangeReport report = new BalanceChangeReport();
            report.setUuid((String) data.get("uuid"));
            report.setUid((Integer) data.get("uid"));
            if(data.getAmount() == null){
                report.setAmount(data.getPayAmount());
            }else{
                report.setAmount(data.getAmount());
            }
            report.setBalanceBefore(data.getBalanceBefore());
            report.setBalanceAfter(data.getBalanceAfter());
            report.setFee(data.getFee());
            report.setReallyAmount(data.getPayAmount());
            report.setOrderType(MsgEnum.Recharge.value());
            report.setTimestamp(new Date());
            reportProducer.sendMessage(ReportEvent.BalanceChange, report);
        }catch (Exception e){
            log.error("report balance change {}", data);
            log.error(e.getMessage(), e);
        }
    }
}
