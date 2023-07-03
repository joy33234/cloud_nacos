package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.RechargePayerMonitorUsernameWhiteList;
import com.seektop.fund.vo.RechargePayerMonitorUsernameWhiteListDO;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface RechargePayerMonitorUsernameWhiteListMapper extends Mapper<RechargePayerMonitorUsernameWhiteList> {

    List<RechargePayerMonitorUsernameWhiteListDO> findWhiteList(@Param(value = "startDate") Date startDate,
                                                                @Param(value = "endDate") Date endDate,
                                                                @Param(value = "username") String username);

}