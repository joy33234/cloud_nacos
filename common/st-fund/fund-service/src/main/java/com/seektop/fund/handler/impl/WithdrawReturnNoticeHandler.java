package com.seektop.fund.handler.impl;

import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.push.Channel;
import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.system.dto.param.ActivityDividendNoticeDO;
import com.seektop.system.dto.result.SystemNoticeTemplateDO;
import com.seektop.system.service.NoticeService;
import com.seektop.system.service.SystemNoticeTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class WithdrawReturnNoticeHandler implements NoticeHandler {

    @Reference(retries = 2, timeout = 3000)
    private SystemNoticeTemplateService systemNoticeTemplateService;
    @Reference(retries = 2, timeout = 3000)
    private NoticeService noticeService;

    @Async
    @Override
    public void doSuccessNotice(NoticeSuccessDto successDto) {
        SystemNoticeTemplateDO snt = null;
        try {
            snt = systemNoticeTemplateService.findById(ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN);
        }
        catch (Exception e) {
            log.error("查询提现退回通知模版异常", e);
        }
        if (ObjectUtils.isEmpty(snt)) {
            log.error("提现退回通知模版在数据库中未配置");
            return;
        }

        BigDecimal amount = successDto.getAmount();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(date);
        String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
        String notice = tNotice.replaceAll("\\[amount\\]", amount.setScale(2, RoundingMode.DOWN).toString());
        String orderId = successDto.getOrderId();

        ActivityDividendNoticeDO noticeDO = new ActivityDividendNoticeDO();
        noticeDO.setAmount(amount);
        noticeDO.setOrderId(orderId);
        noticeDO.setUserId(successDto.getUserId());
        noticeDO.setUsername(successDto.getUserName());
        noticeDO.setChannel(Channel.WithdrawalReturn.value());
        noticeDO.setNoticeType(ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN);
        noticeDO.setContent(notice);
        try {
            noticeService.fundBalanceNotice(noticeDO);
        } catch (Exception e) {
            log.error("noticeService.fundBalanceNotice error", e);
            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", orderId);
        }
    }

    @Async
    @Override
    public void doFailNotice(NoticeFailDto failDto) {
        SystemNoticeTemplateDO snt = null;
        try {
            snt = systemNoticeTemplateService.findById(ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN_FAIL);
        }
        catch (Exception e) {
            log.error("查询提现退回通知模版异常", e);
        }
        if (ObjectUtils.isEmpty(snt)) {
            log.error("提现退回通知模版在数据库中未配置");
            return;
        }

        BigDecimal amount = failDto.getAmount();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(date);
        String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
        String notice = tNotice.replaceAll("\\[amount\\]", amount.setScale(2, RoundingMode.DOWN).toString());
        String orderId = failDto.getOrderId();

        ActivityDividendNoticeDO noticeDO = new ActivityDividendNoticeDO();
        noticeDO.setAmount(amount);
        noticeDO.setOrderId(orderId);
        noticeDO.setUserId(failDto.getUserId());
        noticeDO.setUsername(failDto.getUserName());
        noticeDO.setChannel(Channel.WithdrawalReturnFail.value());
        noticeDO.setNoticeType(ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN_FAIL);
        noticeDO.setContent(notice);

        try {
            noticeService.fundBalanceNotice(noticeDO);
        } catch (Exception e) {
            log.error("noticeService.fundBalanceNotice error", e);
            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", orderId);
        }
    }
}
