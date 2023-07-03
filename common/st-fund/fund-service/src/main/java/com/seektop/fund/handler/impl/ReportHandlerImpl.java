package com.seektop.fund.handler.impl;

import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rest.Result;
import com.seektop.common.utils.DateUtils;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundReportBusiness;
import com.seektop.fund.business.recharge.RechargeExportBusiness;
import com.seektop.fund.business.withdraw.WithdrawDownloadBusiness;
import com.seektop.fund.controller.backend.dto.ReportCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckPageDto;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.ReportHandler;
import com.seektop.fund.vo.FundsCheckReport;
import com.seektop.system.service.GlExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Component("reportHandler")
public class ReportHandlerImpl implements ReportHandler {


    @Reference(retries = 2, timeout = 3000)
    private GlExportService glExportService;
    @Resource
    private RechargeExportBusiness rechargeExportBusiness;
    @Resource
    private WithdrawDownloadBusiness withdrawDownloadBusiness;
    @Resource
    private GlFundReportBusiness glFundReportBusiness;

    public String exportRecharge(ReportCheckDto dto, GlAdminDO adminDO) {
        if (dto.getChannelId() == -1) {
            dto.setChannelId(null);
        }
        if (dto.getDate() == null) {
            dto.setDate(DateUtils.getStartOfDay(new Date()));
        }
        log.info("{} export recharge, time={}", adminDO.getUsername(), DateUtils.getCurrDateStr14());
        rechargeExportBusiness.export(dto, adminDO);
        return LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOADING_CHECK_ON_DOWNLOAD_LIST).withDefaultValue("正在下载，请在下载列表查看").parse(dto.getLanguage());
    }

    public String exportWithdraw(ReportCheckDto dto, GlAdminDO adminDO) {
        if (dto.getChannelId() == -1) {
            dto.setChannelId(null);
        }
        if (dto.getDate() == null) {
            dto.setDate(DateUtils.getStartOfDay(new Date()));
        }
        log.info("{} export recharge, time={}", adminDO.getUsername(), DateUtils.getCurrDateStr14());
        try {
            withdrawDownloadBusiness.asyncWithdrawExport(dto, adminDO);
        }
        catch (GlobalException e) {
            log.error("withdrawDownloadBusiness.asyncWithdrawExport error", e);
        }
        return LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOADING_CHECK_ON_DOWNLOAD_LIST).withDefaultValue("正在下载，请在下载列表查看").parse(dto.getLanguage());
    }

    public PageInfo<FundsCheckReport> fundsCheckList(ReportFundsCheckPageDto dto) {
        if (dto.getDate() == null) {
            dto.setDate(new Date());
        }
        PageInfo<FundsCheckReport> fundsCheckReportList = glFundReportBusiness.findFundsCheckReport(
                dto.getDate(), dto.getChangeType(), dto.getSubType(), dto.getPage(), dto.getSize());
        return fundsCheckReportList;
    }

    public String fundsCheckReport(ReportFundsCheckDto dto, GlAdminDO adminDO) {
        if (dto.getDate() == null) {
            dto.setDate(new Date());
        }
        glFundReportBusiness.export(dto, adminDO);
        return LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOADING_CHECK_ON_DOWNLOAD_LIST).withDefaultValue("正在下载，请在下载列表查看").parse(dto.getLanguage());
    }

}
