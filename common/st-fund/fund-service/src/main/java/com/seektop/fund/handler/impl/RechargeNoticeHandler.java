package com.seektop.fund.handler.impl;

import com.seektop.constant.ProjectConstant;
import com.seektop.constant.notice.NoticeType;
import com.seektop.enumerate.push.Channel;
import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.system.dto.param.ActivityDividendNoticeDO;
import com.seektop.system.dto.result.SystemNoticeTemplateDO;
import com.seektop.system.service.NoticeService;
import com.seektop.system.service.SystemNoticeTemplateService;
import com.seektop.user.dto.param.GlUserNoticeDO;
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
public class RechargeNoticeHandler implements NoticeHandler {

    @Reference(retries = 2, timeout = 3000)
    private SystemNoticeTemplateService systemNoticeTemplateService;
    @Reference(retries = 2, timeout = 3000)
    private NoticeService noticeService;

    @Async
    @Override
    public void doSuccessNotice(NoticeSuccessDto successDto) {
        SystemNoticeTemplateDO snt = null;
        try {
            snt = systemNoticeTemplateService.findById(NoticeType.RECHARGE);
        }
        catch (Exception e) {
            log.error("查询充值通知模版异常", e);
        }
        if (ObjectUtils.isEmpty(snt)) {
            log.error("充值通知模版在数据库中未配置");
            return;
        }

        BigDecimal amount = successDto.getAmount();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(date);
        String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
        String notice = tNotice.replaceAll("\\[amount\\]", amount.setScale(2, RoundingMode.DOWN).toString());
        notice = notice.replaceAll("\\[coin\\]", successDto.getCoin());
        String orderId = successDto.getOrderId();
        Integer userId = successDto.getUserId();

        ActivityDividendNoticeDO noticeDO = new ActivityDividendNoticeDO();
        noticeDO.setAmount(amount);
        noticeDO.setOrderId(orderId);
        noticeDO.setUserId(userId);
        noticeDO.setUsername(successDto.getUserName());
        noticeDO.setChannel(Channel.RechargeSuccessMsg.value());
        noticeDO.setNoticeType(NoticeType.RECHARGE);
        noticeDO.setContent(notice);

        try {
            noticeService.fundBalanceNotice(noticeDO);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", orderId);
        }
    }

    @Async
    @Override
    public void doFailNotice(NoticeFailDto failDto) {
        SystemNoticeTemplateDO snt = null;
        try {
            snt = systemNoticeTemplateService.findById(ProjectConstant.SystemNoticeTempleteId.RECHARGE_FAIL);
        }
        catch (Exception e) {
            log.error("查询充值失败模版异常", e);
        }
        if (ObjectUtils.isEmpty(snt)) {
            log.error("充值失败通知模版在数据库中未配置");
            return;
        }

        BigDecimal amount = failDto.getAmount();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(date);
        String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
        String notice = tNotice.replaceAll("\\[amount\\]", amount.setScale(2, RoundingMode.DOWN).toString());
        notice = notice.replaceAll("\\[coin\\]", failDto.getCoin());
        String orderId = failDto.getOrderId();
        Integer userId = failDto.getUserId();

        GlUserNoticeDO glUserNotice = new GlUserNoticeDO();
        glUserNotice.setLetterType(3);
        glUserNotice.setUserName(failDto.getUserName());
        glUserNotice.setUserId(userId);
        glUserNotice.setCreateTime(new Date());
        glUserNotice.setIsRead(0);
        glUserNotice.setTitle(notice);
        glUserNotice.setType(ProjectConstant.SystemNoticeTempleteId.RECHARGE_FAIL);
        glUserNotice.setStatus(0);
        glUserNotice.setOrderId(orderId);
        glUserNotice.setLastUpdate(glUserNotice.getCreateTime());

        ActivityDividendNoticeDO noticeDO = new ActivityDividendNoticeDO();
        noticeDO.setAmount(amount);
        noticeDO.setOrderId(orderId);
        noticeDO.setUserId(userId);
        noticeDO.setUsername(failDto.getUserName());
        noticeDO.setChannel(Channel.RechargeFailMsg.value());
        noticeDO.setNoticeType(ProjectConstant.SystemNoticeTempleteId.RECHARGE_FAIL);
        noticeDO.setContent(notice);

        try {
            noticeService.fundBalanceNotice(noticeDO);
        }
        catch (Exception e) {
            log.error("userNoticeService.saveNotice or pushService.push error", e);
            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", orderId);
        }
    }
}
