package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.FundProxyAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface FundProxyAccountMapper extends Mapper<FundProxyAccount> {

    @ResultMap("BaseResultMap")
    @Select("select * from gl_fund_proxyaccount where user_id = #{userId} for update")
    FundProxyAccount selectForUpdate(@Param("userId") Integer userId);

}