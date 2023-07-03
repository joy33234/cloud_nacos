package com.seektop.fund.handler;

import com.github.pagehelper.PageInfo;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.dto.ReportCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckPageDto;
import com.seektop.fund.vo.FundsCheckReport;


public interface ReportHandler {

    String exportRecharge(ReportCheckDto dto, GlAdminDO adminDO);

    String exportWithdraw(ReportCheckDto dto, GlAdminDO adminDO);

    String fundsCheckReport(ReportFundsCheckDto dto, GlAdminDO adminDO) throws GlobalException;

    PageInfo<FundsCheckReport> fundsCheckList(ReportFundsCheckPageDto dto);
}
