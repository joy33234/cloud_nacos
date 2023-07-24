package com.seektop.fund.business.withdraw;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawBalanceQueryDO;
import com.seektop.fund.mapper.GlFundMerchantWithdrawMapper;
import com.seektop.fund.model.GlFundMerchantWithdraw;
import com.seektop.fund.vo.WithdrawMerchantBalanceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class GlFundMerchantWithdrawBusiness extends AbstractBusiness<GlFundMerchantWithdraw> {

    @Resource
    private GlFundMerchantWithdrawMapper glFundMerchantWithdrawMapper;

    //提现资金列表
    public PageInfo<WithdrawMerchantBalanceResult> pageList(WithdrawBalanceQueryDO queryDO) {
        PageHelper.startPage(queryDO.getPage(), queryDO.getSize());
        List<WithdrawMerchantBalanceResult> data = glFundMerchantWithdrawMapper.findWithdrawMerchantBalanceList(queryDO.getChannelId(), queryDO.getMerchantCode(), queryDO.getStatus(),queryDO.getCoin());
        return new PageInfo(data);
    }

    @Transactional
    public void syncMerchantBalance(WithdrawMerchantBalanceResult balanceResult) {
        GlFundMerchantWithdraw detail = glFundMerchantWithdrawMapper.selectByPrimaryKey(balanceResult.getMerchantId());
        if (detail == null) {
            detail = new GlFundMerchantWithdraw();
            detail.setMerchantId(balanceResult.getMerchantId());
            detail.setBalance(balanceResult.getBalance());
            detail.setUpdateDate(balanceResult.getUpdateTime());
            detail.setStatus(balanceResult.getStatus());
            glFundMerchantWithdrawMapper.insert(detail);
        } else {
            detail.setBalance(balanceResult.getBalance());
            detail.setUpdateDate(balanceResult.getUpdateTime());
            detail.setStatus(balanceResult.getStatus());
            glFundMerchantWithdrawMapper.updateByPrimaryKey(detail);
        }
    }

    public BigDecimal getTotalBalance() {
        return glFundMerchantWithdrawMapper.getTotalBalance();
    }

}
