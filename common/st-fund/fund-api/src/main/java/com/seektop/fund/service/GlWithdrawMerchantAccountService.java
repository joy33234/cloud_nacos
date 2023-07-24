package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.withdraw.GlWithdrawMerchantAccountDO;

import java.util.List;


/**
 * Created by CodeGenerator on 2018/10/19.
 */
public interface GlWithdrawMerchantAccountService {

    RPCResponse<List<GlWithdrawMerchantAccountDO>> findValidMerchantAccount();

    void doWithdrawMerchantQueryBalance(GlWithdrawMerchantAccountDO accountDO) throws GlobalException;

}

