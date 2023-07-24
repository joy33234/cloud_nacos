package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.ProxyRechargeFee;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;

public interface ProxyRechargeFeeMapper extends Mapper<ProxyRechargeFee> {

    @Select("SELECT sum(rebate) FROM gl_proxy_recharge_fee WHERE user_id=#{userId} AND create_time BETWEEN #{startTime} AND #{endTime}")
    BigDecimal sumRebateByTime(@Param("userId") Integer userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime);
}