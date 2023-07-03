package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.RechargePayerMonitorNameWhiteList;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RechargePayerMonitorNameWhiteListMapper extends Mapper<RechargePayerMonitorNameWhiteList> {

    @Select("select case when count(*) > 0 then 1 else 0 end hasExist from gl_recharge_payer_monitor_name_whitelist where name = #{name}")
    Boolean hasExist(@Param(value = "name") String name);

}