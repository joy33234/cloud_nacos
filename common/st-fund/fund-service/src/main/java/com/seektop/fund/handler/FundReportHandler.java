package com.seektop.fund.handler;

import com.seektop.exception.GlobalException;
import com.seektop.report.fund.FundBaseReport;
import com.seektop.report.fund.HandlerResponse;

public interface FundReportHandler<T extends FundBaseReport> {

    /**
     * 处理财务上报的数据
     *
     * @param report
     * @throws GlobalException
     */
     HandlerResponse handleFund(T report) throws GlobalException;

}