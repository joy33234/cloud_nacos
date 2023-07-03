package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.AgencyRecharge;
import com.seektop.fund.vo.AgencyRechargeQueryDto;
import com.seektop.fund.vo.AgencyRechargeVO;

import java.util.List;

/**
 * 代客充值
 */
public interface AgencyRechargeMapper extends Mapper<AgencyRecharge> {

    List<AgencyRecharge> listAgencyRecharge(AgencyRechargeQueryDto queryDto);

    List<AgencyRechargeVO> findCodes(AgencyRechargeQueryDto queryDto);

}
