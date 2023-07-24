package com.seektop.fund.common;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisTools;
import com.seektop.constant.notice.NoticeType;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.enumerate.fund.C2CEggTypeEnum;
import com.seektop.enumerate.push.Channel;
import com.seektop.report.fund.C2CEggRecordReport;
import com.seektop.report.fund.C2COrderUnlockReport;
import com.seektop.report.fund.C2CRechargePaymentAlertReport;
import com.seektop.report.fund.C2CRechargePaymentTimeoutReport;
import com.seektop.system.dto.param.C2CEggJpushParamDO;
import com.seektop.system.service.JpushNotificationService;
import com.seektop.system.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2COrderUtils {

    private final DynamicKey dynamicKey;
    private final ReportService reportService;

    @DubboReference(timeout = 5000, retries = 1)
    private PushService pushService;
    @DubboReference(timeout = 5000, retries = 1)
    private JpushNotificationService jpushNotificationService;

    @Async
    public void pushEggToApp(Integer recordId, Channel channel) {
        try {
            JSONObject pushDataObj = new JSONObject();
            pushDataObj.put("recordId", recordId);
            pushService.push(channel.value(), null, pushDataObj);
        } catch (Exception ex) {
            log.error("推送彩蛋{} - {}到App内发生异常", recordId, channel.value(), ex);
        }
    }

    @Async
    public void eggJpush(C2CEggTypeEnum typeEnum, BigDecimal awardRate) {
        try {
            JSONObject configObj = dynamicKey.getDynamicValue(DynamicKey.Key.C2C_CONFIG, JSONObject.class);
            if (configObj.containsKey("eggJpushIsEnable") == false) {
                return;
            }
            if (configObj.getBoolean("eggJpushIsEnable") == false) {
                return;
            }
            switch (typeEnum) {
                case WITHDRAW:
                    jpushNotificationService.c2cEgg(new C2CEggJpushParamDO(NoticeType.C2C_WITHDRAW_EGG_JPUSH, awardRate));
                break;
                case RECHARGE:
                    jpushNotificationService.c2cEgg(new C2CEggJpushParamDO(NoticeType.C2C_RECHARGE_EGG_JPUSH, awardRate));
                break;
            }
        } catch (Exception ex) {
            log.error("推送彩蛋到极光发生异常", ex);
        }
    }

    @Async
    public void c2cEggRecordReport(Integer recordId, Long ttl) {
        reportService.c2cEggRecordReport(new C2CEggRecordReport(recordId, ttl - 1000));
    }

    @Async
    public void c2cOrderUnlockReport(String withdrawOrderId) {
        reportService.c2cOrderUnlockReport(new C2COrderUnlockReport(withdrawOrderId, 10000L));
    }

    @Async
    public void paymentAlertAndTimeoutReport(String rechargeOrderId) {
        if (StringUtils.isEmpty(rechargeOrderId)) {
            return;
        }
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (ObjectUtils.isEmpty(configDO)) {
            return;
        }
        // 充值订单付款提醒延时上报
        reportService.c2cRechargePaymentAlertReport(new C2CRechargePaymentAlertReport(rechargeOrderId, Long.valueOf(configDO.getRechargeAlertTime() * 60 * 1000)));
        // 充值订单付款超时延时上报
        reportService.c2cRechargePaymentTimeoutReport(new C2CRechargePaymentTimeoutReport(rechargeOrderId, Long.valueOf(configDO.getRechargePaymentTimeout() * 60 * 1000)));
    }

}