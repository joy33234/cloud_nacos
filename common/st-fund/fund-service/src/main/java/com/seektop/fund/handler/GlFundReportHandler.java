package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.model.FundProxyAccount;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.report.common.BonusReport;
import com.seektop.report.fund.AddCoinReport;
import com.seektop.report.fund.SubCoinReport;
import com.seektop.report.fund.SubCoinReturnReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;

@Slf4j
@Component
public class GlFundReportHandler {
    @Resource
    private ReportService reportService;
    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;
    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;
    @Resource
    private ReportExtendHandler reportExtendHandler;
    @Autowired
    private FundChangeToolHandler toolHandler;

    /**
     * 减币退回 一次上报
     *
     * @param user
     * @param changeRequest
     */
    public void reportSubCoinReturn(GlUserDO user, GlFundChangeRequest changeRequest) {
        //上报一笔新的单号
        SubCoinReturnReport report = new SubCoinReturnReport();
        //修改订单号
        report.setUuid("RB" + changeRequest.getOrderId());
        report.setUid(user.getId());
        report.setUserName(user.getUsername());
        report.setUserType(UserTypeEnum.valueOf(user.getUserType()));
        report.setParentId(user.getParentId());
        report.setParentName(user.getParentName());
        report.setAmount(changeRequest.getAmount().multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        report.setRegTime(user.getRegisterDate());
//        //减币成功，记录前后账变信息
        BigDecimal balance = glFundUserAccountBusiness.getUserBalance(user.getId());
        report.setBalanceAfter(balance.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        report.setBalanceBefore(balance.subtract(changeRequest.getAmount()).multiply(BigDecimalUtils.TEN_THOUSAND).longValue());

        report.setCreateTime(new Date());
        report.setTimestamp(new Date());
        report.setFinishTime(new Date());

        report.setRemark(changeRequest.getAmount().compareTo(BigDecimal.ZERO) == 1 ? "系统加币" : "系统减币");
        Integer subType = changeRequest.getSubType();
        if (!ObjectUtils.isEmpty(subType)) {
            report.setSubType(subType.toString());
        }
        report.setStatus(1);
        report.setIsFake(user.getIsFake());
        log.info("doSubCoinRecoverIfNeed = {}", report);
        reportExtendHandler.extendReport(report);
        reportService.reportSubCoinReturn(report);
    }

    /**
     * 减币失败
     *
     * @param orderId
     */
    public void reportSubCoinFail(String orderId) {
        SubCoinReport report = new SubCoinReport();
        report.setUuid(orderId);
        report.setStatus(2);
        reportService.reportSubCoin(report);
    }

    /**
     * 红利上报状态更新
     *
     * @param orderId
     * @param status
     * @throws GlobalException
     */
    public void reportBonusStatus(String orderId, int status) throws GlobalException {
        log.info("红利上报状态更新：{} 状态 {}", orderId, status);
        BonusReport report = new BonusReport();
        report.setUuid(orderId);
        report.setStatus(status);
        reportService.bonusReport(report);
    }

    public void reportSubCoin(GlFundChangeRequest changeRequest, GlUserDO user, int status) throws GlobalException {
        SubCoinReport report = new SubCoinReport();
        report.setUuid(changeRequest.getOrderId());
        report.setUid(user.getId());
        report.setUserName(user.getUsername());
        report.setUserType(UserTypeEnum.valueOf(changeRequest.getUserType()));
        report.setParentId(user.getParentId());
        report.setParentName(user.getParentName());
        report.setAmount(changeRequest.getAmount().multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        report.setRegTime(user.getRegisterDate());
        //减币成功，记录前后账变信息
        BigDecimal balance = glFundUserAccountBusiness.getUserBalance(user.getId());
        report.setBalanceAfter(balance.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        report.setBalanceBefore(balance.add(changeRequest.getAmount().abs()).multiply(BigDecimalUtils.TEN_THOUSAND).longValue());

        //代理上报过后信用余额
        if (user.getUserType() == UserConstant.Type.PROXY) {
            FundProxyAccount proxyAccount = fundProxyAccountBusiness.findById(user.getId());
            BigDecimal creditAmount = proxyAccount.getCreditAmount();

            BigDecimal creditAmountAfter = balance.compareTo(BigDecimal.ZERO) >= 0 ? creditAmount : creditAmount.subtract(balance.abs());
            report.setCreditBalanceAfter(creditAmountAfter.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        }


        report.setCreateTime(changeRequest.getCreateTime());
        report.setTimestamp(changeRequest.getCreateTime());
        report.setFinishTime(new Date());

        report.setRemark(changeRequest.getAmount().compareTo(BigDecimal.ZERO) == 1 ? "系统加币" : "系统减币");
        if (toolHandler.isBonus(changeRequest)) {
            report.setRemark("系统加币");
        }
        Integer subType = changeRequest.getSubType();
        if (!ObjectUtils.isEmpty(subType)) {
            report.setSubType(subType.toString());
        }
        //状态为处理中
        report.setStatus(status);
        report.setIsFake(user.getIsFake());
        reportExtendHandler.extendReport(report);
        log.info("subCoin report = {}", report);
        reportService.reportSubCoin(report);
    }

    public void reportAddCoin(GlFundChangeRequest changeRequest, GlUserDO user, Integer subType, BigDecimal balance, String remark) throws GlobalException {
        AddCoinReport addCoinReport = new AddCoinReport();
        addCoinReport.setUuid(changeRequest.getOrderId());
        addCoinReport.setUid(user.getId());
        addCoinReport.setUserName(user.getUsername());
        addCoinReport.setUserType(UserTypeEnum.valueOf(changeRequest.getUserType()));
        addCoinReport.setParentId(user.getParentId());
        addCoinReport.setParentName(user.getParentName());
        addCoinReport.setRegTime(user.getRegisterDate());
        addCoinReport.setCreateTime(new Date());
        addCoinReport.setTimestamp(new Date());
        addCoinReport.setFinanceAdjustReason(changeRequest.getFinanceAdjustReason());
        addCoinReport.setRemark(remark);
        if (!ObjectUtils.isEmpty(subType)) {
            addCoinReport.setSubType(subType.toString());
        }
        addCoinReport.setStatus(1);
        addCoinReport.setAmount(changeRequest.getAmount().multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        addCoinReport.setBalanceAfter(balance.multiply(BigDecimal.valueOf(10000)).longValue());
        addCoinReport.setBalanceBefore(balance.subtract(changeRequest.getAmount()).multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        //代理上报过后信用余额
        if (user.getUserType() == UserConstant.Type.PROXY) {
            FundProxyAccount proxyAccount = fundProxyAccountBusiness.findById(user.getId());
            BigDecimal creditAmount = proxyAccount.getCreditAmount();
            BigDecimal creditAmountAfter = balance.compareTo(BigDecimal.ZERO) >= 0 ? creditAmount : creditAmount.subtract(balance.abs());
            addCoinReport.setCreditBalanceAfter(creditAmountAfter.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
        }

        addCoinReport.setCreateTime(changeRequest.getCreateTime());
        addCoinReport.setTimestamp(changeRequest.getCreateTime());
        addCoinReport.setFinishTime(new Date());

        addCoinReport.setIsFake(user.getIsFake());
        reportExtendHandler.extendReport(addCoinReport);
        log.info("AddCoinReport = {}", JSON.toJSONString(addCoinReport));
        reportService.reportAddSubCoin(addCoinReport);
    }

    public void reportBonus(GlFundChangeRequest changeRequest, GlUserDO user, Integer subType,
                            BigDecimal balance, String remark) {
        Date now = new Date();
        //加币计入红利
        BonusReport report = new BonusReport();
        report.setUuid(changeRequest.getOrderId());
        report.setUid(user.getId());
        report.setUserName(user.getUsername());
        report.setUserType(UserTypeEnum.valueOf(user.getUserType()));
        report.setParentId(user.getParentId());
        report.setParentName(user.getParentName());
        // todo 财务更新
        //report.setCoin(changeRequest.getCoinCode());
        report.setCoin("CNY");
        report.setAmount(changeRequest.getAmount().movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        report.setRegTime(user.getRegisterDate());
        report.setFinanceAdjustReason(changeRequest.getFinanceAdjustReason());

        report.setBalanceAfter(balance.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        report.setBalanceBefore(balance.subtract(changeRequest.getAmount()).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        report.setCreateTime(changeRequest.getCreateTime());
        report.setTimestamp(changeRequest.getCreateTime());
        report.setFinishTime(now);
        if (subType != FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()) {
            report.setRemark(remark);
        }
        if (!ObjectUtils.isEmpty(subType)) {
            report.setSubType(subType.toString());
        }
        report.setStatus(1);
        report.setIsFake(user.getIsFake());

        reportExtendHandler.extendReport(report);
        log.info("BonusReport = {}", JSON.toJSONString(report));
        reportService.bonusReport(report);
    }
}
