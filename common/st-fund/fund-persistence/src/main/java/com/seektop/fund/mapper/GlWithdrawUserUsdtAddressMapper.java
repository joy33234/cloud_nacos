package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawUserUsdtAddress;
import com.seektop.fund.vo.UserBindQueryDO;
import com.seektop.fund.vo.UserBindUsdtDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GlWithdrawUserUsdtAddressMapper extends Mapper<GlWithdrawUserUsdtAddress> {

    Integer queryUserUsdtCount(@Param("userId") Integer userId);

    List<UserBindUsdtDO> usdtList(UserBindQueryDO queryDO);

    @Select("select case when count(id) > 0 then 1 else 0 end as hasExist from gl_withdraw_user_usdt_address where coin = #{coin} and protocol = #{protocol} and address = #{address} and status = 0")
    Boolean isExist(@Param(value = "coin") String coin,
                    @Param(value = "protocol") String protocol,
                    @Param(value = "address") String address);

}