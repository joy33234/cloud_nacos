package com.seektop.fund.service.impl;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.dto.param.withdraw.DeleteUsdtAddressDto;
import com.seektop.fund.dto.result.withdraw.WithdrawUserUsdtAddressDO;
import com.seektop.fund.model.GlWithdrawUserUsdtAddress;
import com.seektop.fund.service.WithdrawUserUsdtAddressService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.List;

@DubboService(timeout = 5000, interfaceClass = WithdrawUserUsdtAddressService.class, validation = "true")
public class WithdrawUserUsdtAddressServiceIml implements WithdrawUserUsdtAddressService {

    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;

    @Override
    public RPCResponse<List<WithdrawUserUsdtAddressDO>> findUsdtAddressListByUserId(Integer userId) {
        List<GlWithdrawUserUsdtAddress> usdtAddressList = glWithdrawUserUsdtAddressBusiness.findByUserId(userId, 0);
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(usdtAddressList, WithdrawUserUsdtAddressDO.class));
    }

    @Override
    public RPCResponse<Boolean> delUsdtAddress(DeleteUsdtAddressDto dto) {
        boolean flag = glWithdrawUserUsdtAddressBusiness.doDelectUsdtAddress(dto);
        return RPCResponseUtils.buildSuccessRpcResponse(flag);
    }

    @Override
    public RPCResponse<Boolean> ApproveFailUsdtAddress(DeleteUsdtAddressDto dto) {
        GlWithdrawUserUsdtAddress glWithdrawUserUsdtAddress = new GlWithdrawUserUsdtAddress();
        glWithdrawUserUsdtAddress.setId(Integer.valueOf(dto.getOptData()));
        glWithdrawUserUsdtAddress.setStatus(0);
        glWithdrawUserUsdtAddressBusiness.updateByPrimaryKeySelective(glWithdrawUserUsdtAddress);
        glWithdrawUserUsdtAddressBusiness.updateUserManage(dto);
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }

    @Override
    public RPCResponse<Boolean> updateUSDTRate() {
        boolean buyFlag = glWithdrawUserUsdtAddressBusiness.setBuyUSDTRate();

        boolean sellFlag = glWithdrawUserUsdtAddressBusiness.setSellUSDTRate();

        if (buyFlag && sellFlag) {
            return RPCResponseUtils.buildSuccessRpcResponse(true);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(false);
    }

    @Override
    public RPCResponse<List<WithdrawUserUsdtAddressDO>> findUsdtAddressListByAddress(String address) {
        List<GlWithdrawUserUsdtAddress> usdtAddressList = glWithdrawUserUsdtAddressBusiness.findByAddress(address, 0);
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(usdtAddressList, WithdrawUserUsdtAddressDO.class));
    }


}
