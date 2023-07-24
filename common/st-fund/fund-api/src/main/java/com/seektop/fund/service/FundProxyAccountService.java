package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.agent.TransferStatusEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.account.FundProxyAccountDto;
import com.seektop.fund.dto.param.proxy.FundProxyAccountDO;
import com.seektop.fund.dto.param.proxy.QuotaAdjDO;
import com.seektop.fund.dto.param.proxy.TransferDO;
import com.seektop.report.user.UserSynch;

import java.math.BigDecimal;
import java.util.Date;

public interface FundProxyAccountService {

    RPCResponse<FundProxyAccountDO> findById(Integer userId);

    RPCResponse<FundProxyAccountDO> selectForUpdate(Integer userId);

    RPCResponse<BigDecimal> validBalanceForUpdate(Integer userId);

    RPCResponse<Void> transferApply(String orderNo, GlUserDO proxy, GlUserDO target, GlAdminDO admin, BigDecimal amount, String remark, Date now);

    RPCResponse<Void> transferReview(TransferDO transferDO, TransferStatusEnum status) throws GlobalException;

    RPCResponse<Void> quotaAdj(QuotaAdjDO quotaDO);

    RPCResponse<Void> quotaAdjLog(QuotaAdjDO quotaDO);

    RPCResponse<Void> updateFundProxyAccount(FundProxyAccountDO fundProxyAccountDO, UserSynch userSynch);

    RPCResponse<Void> rechargeRebateApprovePass(Integer userId, BigDecimal amount);
    /**
     * 创建代理fundAccount，并代理相关额度及权限表
     * @param dto
     * @return
     */
    RPCResponse<Boolean> createFundAccount(FundProxyAccountDto dto);
}
