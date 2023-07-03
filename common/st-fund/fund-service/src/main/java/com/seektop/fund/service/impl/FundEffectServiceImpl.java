package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseCode;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.dto.param.effect.CleanWithdrawEffectDto;
import com.seektop.fund.dto.param.effect.EffectApproveDto;
import com.seektop.fund.dto.param.effect.EffectUpdateStatus;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.handler.UserWithdrawEffectHandler;
import com.seektop.fund.service.FundEffectService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Slf4j
@Service(timeout = 5000, interfaceClass = FundEffectService.class)
public class FundEffectServiceImpl implements FundEffectService {
    @Autowired
    private UserWithdrawEffectHandler userWithdrawEffectHandler;
    @Autowired
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;

    @Override
    public void cleanWithdrawEffect(CleanWithdrawEffectDto cleanWithdrawEffectDto) throws GlobalException {
        log.info("start cleanWithdrawEffect = {}", cleanWithdrawEffectDto);
        try {
            userWithdrawEffectHandler.cleanWithdrawEffect(cleanWithdrawEffectDto.getUserDO(),
                    cleanWithdrawEffectDto.getRemark(), cleanWithdrawEffectDto.getOperator(),cleanWithdrawEffectDto.getCoin());
        } catch (GlobalException e) {
            log.error(e.getExtraMessage(), e);
            throw e;
        }
    }

    @Override
    public void bettingBalanceRemove(EffectApproveDto effectApproveDto) throws GlobalException {
        log.info("start bettingBalanceRemove = {}", effectApproveDto);
        userWithdrawEffectHandler.bettingBalanceRemove(effectApproveDto.getRevAmount(), effectApproveDto.getRemark(),
                effectApproveDto.getOperator(), effectApproveDto.getOrderNo(), effectApproveDto.getUserDO(), effectApproveDto.getCoinCode());
    }

    @Override
    public void bettingBalanceRecover(EffectApproveDto effectApproveDto) throws GlobalException {
        log.info("start bettingBalanceRecover = {}", effectApproveDto);
        userWithdrawEffectHandler.bettingBalanceRecover(effectApproveDto.getRevAmount(), effectApproveDto.getRemark(),
                effectApproveDto.getOperator(), effectApproveDto.getOrderNo(), effectApproveDto.getUserDO(),effectApproveDto.getCoinCode());
    }

    @Override
    public void adjustBetBalance(EffectApproveDto effectApproveDto) throws GlobalException {
        log.info("start adjustBetBalance = {}", effectApproveDto);
        userWithdrawEffectHandler.adjustBetBalanceApprove(effectApproveDto.getRevAmount(), effectApproveDto.getRemark(),
                effectApproveDto.getOperator(), effectApproveDto.getUserDO(),effectApproveDto.getCoinCode());
    }

    @Override
    public void updateRecordStatusByOrderId(EffectUpdateStatus effectUpdateStatus){
        userWithdrawEffectHandler.updateRecordStatusByOrderId(effectUpdateStatus.getOrderId(), effectUpdateStatus.getStatus(), effectUpdateStatus.getRemark());
    }

    @Override
    public RPCResponse<List<GlWithdrawEffectBetDO>> getUserWithdrawEffect(Integer userId) {
        try {
            List<GlWithdrawEffectBetDO> effectBetDO = glWithdrawEffectBetBusiness.getWithdrawEffectBetDO(userId);
            return RPCResponseUtils.buildSuccessRpcResponse(effectBetDO);
        } catch (GlobalException e) {
            log.error(e.getExtraMessage(), e);
        }catch (Exception e){
            log.error(e.getMessage(), e);
        }
        RPCResponse response = RPCResponse.newBuilder().fail(RPCResponseCode.FAIL_DEFAULT).build();
        return response;
    }

    @Override
    public RPCResponse<GlWithdrawEffectBetDO> getUserWithdrawEffect(Integer userId,String coin) {
        try {
            GlWithdrawEffectBetDO effectBetDO = glWithdrawEffectBetBusiness.getWithdrawEffectBetDO(userId,coin);
            return RPCResponseUtils.buildSuccessRpcResponse(effectBetDO);
        } catch (GlobalException e) {
            log.error(e.getExtraMessage(), e);
        }catch (Exception e){
            log.error(e.getMessage(), e);
        }
        RPCResponse response = RPCResponse.newBuilder().fail(RPCResponseCode.FAIL_DEFAULT).build();
        return response;
    }
}
