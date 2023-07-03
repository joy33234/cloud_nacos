package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.dto.ReportCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckPageDto;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.ReportHandler;
import com.seektop.fund.vo.FundsCheckReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 财务报表
 */
@RestController
@RequestMapping("/manage/fund/report")
@Slf4j
public class FundReportController extends FundBackendBaseController {

    @Resource
    private ReportHandler reportHandler;
    @Autowired
    private RedisService redisService;

    /**
     * 资金调整报表
     */
    @PostMapping(value = "/funds/check/list", produces = "application/json;charset=utf-8")
    public Result fundsCheckList(@Validated ReportFundsCheckPageDto dto) {
        PageInfo<FundsCheckReport> result = reportHandler.fundsCheckList(dto);
        return Result.genSuccessResult(result);
    }

    /**
     * 资金调整报表导出
     */
    @PostMapping(value = "/funds/check/download/excel", produces = "application/json;charset=utf-8")
    public Result fundsCheckReport(@Validated ReportFundsCheckDto dto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        // 限制导出
        String lockKey = "FUND_REPORT_FUNDS_CHECK_EXPORT_LOCK_" + adminDO.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            Result result = Result.genFailResult("5分钟只能导出一次");
            result.setKeyConfig(FundLanguageMvcEnum.DOWNLOAD_TIME_LIMIT_FIVE_MINUTE);
            return result;
        }
        redisService.set(lockKey, "1", 300);
        return Result.genSuccessResult(reportHandler.fundsCheckReport(dto, adminDO));
    }

    /**
     * 充值订单导出  导出指定时间当天的数据
     */
    @PostMapping(value = "/recharge/export", produces = "application/json;charset=utf-8")
    public Result exportRecharge(@Validated ReportCheckDto dto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        // 限制导出
        String lockKey = "FUND_REPORT_RECHARGE_EXPORT_LOCK_" + adminDO.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOAD_TIME_LIMIT_FIVE_MINUTE).withDefaultValue("5分钟只能导出一次").parse(dto.getLanguage()));
        }
        redisService.set(lockKey, "1", 300);
        return Result.genSuccessResult(reportHandler.exportRecharge(dto, adminDO));
    }

    /**
     *提现订单导出  导出指定时间当天的数据
     */
    @PostMapping(value = "/withdraw/export", produces = "application/json;charset=utf-8")
    public Result exportWithdraw(@Validated ReportCheckDto dto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        // 限制导出
        String lockKey = "FUND_REPORT_WITHDRAW_EXPORT_LOCK_" + adminDO.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOAD_TIME_LIMIT_FIVE_MINUTE).withDefaultValue("5分钟只能导出一次").parse(dto.getLanguage()));
        }
        redisService.set(lockKey, "1", 300);
        return Result.genSuccessResult(reportHandler.exportWithdraw(dto, adminDO));
    }
}