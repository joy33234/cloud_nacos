package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundUserAccount;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户中心钱包
 */
public interface GlFundUserAccountMapper extends Mapper<GlFundUserAccount> {
    @Results(id = "glFundUserAccount", value = {
            @Result(property = "userId", column = "user_id", id = true),
            @Result(property = "balance", column = "balance"),
            @Result(property = "validBalance", column = "valid_balance"),
            @Result(property = "freezeBalance", column = "freeze_balance"),
            @Result(property = "lastUpdate", column = "last_update"),
            @Result(property = "lastRecharge", column = "last_recharge"),
    })
    @Select("select * from gl_fund_useraccount where user_id = #{userId} for update")
    GlFundUserAccount selectForUpdate(@Param("userId") Integer userId);

    @Update("update gl_fund_useraccount set balance=balance+#{amount}, last_update=#{now} where user_id=#{userId}")
    void addBalance(@Param("userId") Integer userId, @Param("amount") BigDecimal amount, @Param("now") Date now);

    @Select("select balance from gl_fund_useraccount where user_id = #{userId}")
    BigDecimal getBalance(Integer userId);
}