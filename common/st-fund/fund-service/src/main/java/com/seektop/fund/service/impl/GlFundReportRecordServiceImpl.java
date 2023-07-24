package com.seektop.fund.service.impl;

import com.seektop.fund.business.GlFundReportRecordBusiness;
import com.seektop.fund.service.GlFundReportRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@DubboService(timeout = 5000, interfaceClass = GlFundReportRecordService.class)
public class GlFundReportRecordServiceImpl implements GlFundReportRecordService {

    @Resource
    private GlFundReportRecordBusiness glFundReportRecordBusiness;

    @Override
    public void deleteLogs(Date date) {
        glFundReportRecordBusiness.deleteLogs(date);
    }
}
