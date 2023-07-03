package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseCode;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.FundRequestBusiness;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.dto.param.account.ReduceRequestDto;
import com.seektop.fund.dto.param.account.UserBalanceRequestDO;
import com.seektop.fund.mapper.GlFundChangeRequestMapper;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.fund.service.GlFundRequestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Service(timeout = 5000, interfaceClass = GlFundRequestService.class)
public class GlFundRequestServiceImpl implements GlFundRequestService {

    @Resource
    private GlFundBusiness glFundBusiness;
    @Resource
    private FundRequestBusiness fundRequestBusiness;
    @Resource
    private GlFundChangeRequestMapper glFundChangeRequestMapper;

    @Override
    public RPCResponse<Void> adjustUserBalance(UserBalanceRequestDO dto) {
        try {
            if (dto.getChangeType() == null) {
                dto.setChangeType(FundConstant.ChangeOperateType.ADD_NOT_INCLUDE_PROFIT);
            }
            glFundBusiness.adjustUserBalance(dto.getUser(), dto.getChangeType(), dto.getBalance(), dto.getCreator(), dto.getSubChangeType(), dto.getRemark());
            return RPCResponseUtils.buildSuccessRpcResponse(null);
        } catch (GlobalException e) {
            log.error(e.getExtraMessage(), e);
            RPCResponse response = RPCResponse.newBuilder().fail(RPCResponseCode.FAIL_DEFAULT).build();
            return response;
        }
    }

    @Override
    public RPCResponse<BigDecimal> sumWrongRechargeTotal(Date startDate, Date endDate, Integer userId) {
        BigDecimal bigDecimal = glFundChangeRequestMapper.sumWrongRechargeTotal(startDate, endDate, userId);
        return RPCResponseUtils.buildSuccessRpcResponse(bigDecimal == null ? BigDecimal.ZERO : bigDecimal);
    }

    @Override
    public RPCResponse<Boolean> addReduceRequest(ReduceRequestDto dto) throws GlobalException {
        List<GlFundChangeRequest> requestList = fundRequestBusiness.getRequestList(dto);
        glFundBusiness.doFundChargeSubmit(requestList);
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }
}
