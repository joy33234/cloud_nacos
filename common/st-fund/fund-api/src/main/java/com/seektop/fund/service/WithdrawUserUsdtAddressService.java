package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.fund.dto.param.withdraw.DeleteUsdtAddressDto;
import com.seektop.fund.dto.result.withdraw.WithdrawUserUsdtAddressDO;

import java.util.List;

public interface WithdrawUserUsdtAddressService {

    RPCResponse<List<WithdrawUserUsdtAddressDO>> findUsdtAddressListByUserId(Integer userId);

    RPCResponse<Boolean> delUsdtAddress(DeleteUsdtAddressDto dto);

    RPCResponse<Boolean> ApproveFailUsdtAddress(DeleteUsdtAddressDto dto);

    RPCResponse<Boolean> updateUSDTRate();

    RPCResponse<List<WithdrawUserUsdtAddressDO>> findUsdtAddressListByAddress(String address);
}
