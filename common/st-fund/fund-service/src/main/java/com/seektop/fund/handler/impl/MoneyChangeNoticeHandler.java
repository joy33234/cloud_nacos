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
public class MoneyChangeNoticeHandler implements NoticeHandler {

    @Reference(retries = 2, timeout = 3000)
    private SystemNoticeTemplateService systemNoticeTemplateService;
    @Reference(retries = 2, timeout = 3000)
    private NoticeService noticeService;

    @Async
    @Override
    public void doSuccessNotice(NoticeSuccessDto successDto) {
        Integer type = successDto.getType();
        SystemNoticeTemplateDO snt = null;
        try {
            snt = systemNoticeTemplateService.findById(type);
        }
        catch (Exception e) {
            log.error("查询通知模版异常", e);
        }
        if (ObjectUtils.isEmpty(snt)) {
            log.error("通知模版在数据库中未配置");
            return;
        }

        BigDecimal amount = successDto.getAmount();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startTime = sdf.format(date);
        String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
        String rNotice = tNotice.replaceAll("\\[subTypeName\\]", successDto.getSubTypeName());
        String notice = rNotice.replaceAll("\\[amount\\]", amount.setScale(2, RoundingMode.DOWN).toString());
        String orderId = successDto.getOrderId();

        ActivityDividendNoticeDO noticeDO = new ActivityDividendNoticeDO();
        noticeDO.setAmount(amount);
        noticeDO.setOrderId(orderId);
        noticeDO.setUserId(successDto.getUserId());
        noticeDO.setUsername(successDto.getUserName());
        boolean condition = ProjectConstant.SystemNoticeTempleteId.BONUS_NO_REMARK == type;
        Integer noticeType = condition ? ProjectConstant.SystemNoticeTempleteId.BONUS : ProjectConstant.SystemNoticeTempleteId.DEDUCTION;
        Integer channelValue = condition ? Channel.Bonus.value() : Channel.Deduction.value();
        noticeDO.setChannel(channelValue);
        noticeDO.setNoticeType(noticeType);
        noticeDO.setContent(notice);
        try {
            noticeService.fundBalanceNotice(noticeDO);
        } catch (Exception e) {
            log.error("noticeService.fundBalanceNotice error", e);
            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", orderId);
        }
    }

    @Override
    public void doFailNotice(NoticeFailDto failDto) {

    }
}
