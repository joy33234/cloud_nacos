package com.seektop.fund.business.proxy;

import com.seektop.agent.dto.ValidWithdrawalDto;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.OrderPrefix;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.agent.TransferStatusEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.dto.param.account.FundProxyAccountDto;
import com.seektop.fund.mapper.FundProxyAccountMapper;
import com.seektop.fund.model.FundProxyAccount;
import com.seektop.report.agent.ProxyTransferInReport;
import com.seektop.report.agent.ProxyTransferOutReport;
import com.seektop.report.user.BalanceDetailDO;
import com.seektop.report.user.UserSynch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;


@Slf4j
@Component
public class FundProxyAccountBusiness extends AbstractBusiness<FundProxyAccount> {

    @Resource
    private FundProxyAccountMapper fundProxyAccountMapper;

    @Resource
    private ReportService reportService;

    @Resource
    private RedisService redisService;
    @Autowired
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    public FundProxyAccount findById(Integer userId) {
        return fundProxyAccountMapper.selectByPrimaryKey(userId);
    }

    public FundProxyAccount selectForUpdate(Integer userId) {
        return fundProxyAccountMapper.selectForUpdate(userId);
    }

    public Boolean addValidWithdrawal(ValidWithdrawalDto dto) {
        FundProxyAccount proxyAccount = fundProxyAccountMapper.selectForUpdate(dto.getUserId());
        if (null == proxyAccount) {
            return false;
        }
        BigDecimal validWithdraw = proxyAccount.getValidWithdrawal().add(dto.getAmount());
        proxyAccount.setValidWithdrawal(validWithdraw);
        proxyAccount.setLastUpdate(new Date());
        updateByPrimaryKeySelective(proxyAccount);
        return true;
    }

    public void transferOutRecordReport(String orderNo, GlUserDO proxy, GlUserDO target, BigDecimal amount, BigDecimal balance, TransferStatusEnum status, String remark, GlAdminDO admin,String coinCode, Date now) {
        ProxyTransferOutReport proxyTransferOutReport = new ProxyTransferOutReport();
        proxyTransferOutReport.setUuid(orderNo);
        proxyTransferOutReport.setCoin(coinCode);
        proxyTransferOutReport.setUid(proxy.getId());
        proxyTransferOutReport.setUserId(proxy.getId());
        proxyTransferOutReport.setUserType(UserTypeEnum.PROXY);
        proxyTransferOutReport.setUserName(proxy.getUsername());
        proxyTransferOutReport.setParentId(proxy.getParentId());
        proxyTransferOutReport.setParentName(proxy.getParentName());
        proxyTransferOutReport.setStatus(status);
        proxyTransferOutReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        //操作代理账户余额的前后变化
        proxyTransferOutReport.setBalanceBefore(balance.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        proxyTransferOutReport.setBalanceAfter((balance.subtract(amount)).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        //目标用户信息
        proxyTransferOutReport.setTargetUserId(target.getId());
        proxyTransferOutReport.setTargetUserName(target.getUsername());
        proxyTransferOutReport.setUserType(UserTypeEnum.PROXY);
        proxyTransferOutReport.setSubType("转账给代理（" + target.getUsername() + "）");
        proxyTransferOutReport.setRemark(remark);
        //操作人信息（默认为操作代理）
        if (!ObjectUtils.isEmpty(admin)) {
            proxyTransferOutReport.setOptUserId(admin.getUserId());
            proxyTransferOutReport.setOptUserName(admin.getUsername());
        } else {
            proxyTransferOutReport.setOptUserId(proxy.getId());
            proxyTransferOutReport.setOptUserName(proxy.getUsername());
        }
        proxyTransferOutReport.setCreateTime(now);
        proxyTransferOutReport.setTimestamp(now);
        proxyTransferOutReport.setFinishTime(now);
        reportService.proxyTransferOutReport(proxyTransferOutReport);
    }

    public void transferInRecordReport(GlUserDO proxy, GlUserDO targetUser, BigDecimal amount, BigDecimal balance, BigDecimal creditBalanceAfter, String subType, String remarks, String optUserName,String coin, Date now) {
        ProxyTransferInReport proxyTransferInReport = new ProxyTransferInReport();
        proxyTransferInReport.setUuid(redisService.getTradeNo(OrderPrefix.ZZ.getCode()));
        proxyTransferInReport.setCoin(coin);
        proxyTransferInReport.setUid(targetUser.getId());
        proxyTransferInReport.setUserId(targetUser.getId());
        proxyTransferInReport.setUserType(UserTypeEnum.PROXY);
        proxyTransferInReport.setUserName(targetUser.getUsername());
        proxyTransferInReport.setParentId(targetUser.getParentId());
        proxyTransferInReport.setParentName(targetUser.getParentName());
        proxyTransferInReport.setStatus(TransferStatusEnum.SUCCESS);
        proxyTransferInReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        proxyTransferInReport.setBalanceBefore(balance.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        proxyTransferInReport.setBalanceAfter(balance.add(amount).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        proxyTransferInReport.setCreditBalanceAfter(creditBalanceAfter.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        proxyTransferInReport.setTransferUserId(proxy.getId());
        proxyTransferInReport.setTransferUserName(proxy.getUsername());
        proxyTransferInReport.setSubType(subType);
        proxyTransferInReport.setOptUserName(optUserName);
        proxyTransferInReport.setCreateTime(now);
        proxyTransferInReport.setTimestamp(now);
        proxyTransferInReport.setFinishTime(now);
        proxyTransferInReport.setRemark(remarks);
        reportService.proxyTransferInReport(proxyTransferInReport);
    }

    /**
     * 上报更新余额和可提现额度
     *
     * @param userId
     * @param balance
     * @param validWithdrawal
     */
    public void esUserReport(Integer userId, String coin, BigDecimal balance, BigDecimal validWithdrawal) {
        UserSynch esUser = new UserSynch();
        esUser.setId(userId);
        //可用余额
        if (balance != null) {
            esUser.setBalanceDetail(Arrays.asList(new BalanceDetailDO(coin, balance)));
            esUser.setBalance(balance);
        }
        //可提现额度
        if (null != validWithdrawal) esUser.setValid_withdrawal(validWithdrawal);
        reportService.userSynch(esUser);
    }

    public void save(FundProxyAccountDto dto) {
        GlUserDO user = dto.getUser();
        glFundUserAccountBusiness.createFundAccount(user, DigitalCoinEnum.CNY, dto.getCreator());
        Integer proxyType = dto.getProxyType();
        //代理相关额度及权限表
        FundProxyAccount glFundProxyAccount = new FundProxyAccount();
        glFundProxyAccount.setUserId(user.getId());
        glFundProxyAccount.setType(ObjectUtils.isEmpty(proxyType) ? UserConstant.ProxyType.OUTTER : proxyType);
        glFundProxyAccount.setCreditAmount(BigDecimal.ZERO);
        glFundProxyAccount.setValidWithdrawal(BigDecimal.ZERO);
        glFundProxyAccount.setPayoutStatus(ProjectConstant.switchCase.ON);
        glFundProxyAccount.setTransferProxyStatus(ProjectConstant.switchCase.OFF);
        glFundProxyAccount.setCreateProxyStatus(ProjectConstant.switchCase.OFF);
        fundProxyAccountMapper.insertSelective(glFundProxyAccount);
    }


    public Boolean addValidWithdrawalSyncEs(ValidWithdrawalDto dto) {
        FundProxyAccount proxyAccount = fundProxyAccountMapper.selectForUpdate(dto.getUserId());
        if (null == proxyAccount) {
            return false;
        }
        BigDecimal validWithdraw = proxyAccount.getValidWithdrawal().add(dto.getAmount());
        proxyAccount.setValidWithdrawal(validWithdraw);
        proxyAccount.setLastUpdate(new Date());
        updateByPrimaryKeySelective(proxyAccount);

        esUserReport(dto.getUserId(), null,null,validWithdraw);
        return true;
    }
}