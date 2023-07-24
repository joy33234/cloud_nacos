package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.ProxyCreditPayoutLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface ProxyCreditPayoutLogMapper extends Mapper<ProxyCreditPayoutLog> {

    @Select("select sum(amount) from gl_proxy_credit_payout_log where 1=1 AND proxy_id = #{proxyId} AND create_time >= #{startDate} AND create_time <= #{endDate}")
    BigDecimal sumAllPayoutAmount(@Param("startDate") Date startDate, @Param("endDate") Date endDate, @Param("proxyId") Integer proxyId);

    List<ProxyCreditPayoutLog> findByTime(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

}