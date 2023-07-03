package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundUserCoinAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 用户中心钱包
 */
public interface GlFundUserCoinAccountMapper extends Mapper<GlFundUserCoinAccount> {

    @Update("update gl_fund_user_coin_account set coin_balance = #{coinBalance}, last_update = #{lastUpdate}, last_update_remark = #{lastUpdateRemark} where user_id = #{userId} and coin_balance = #{beforeBalance} ")
    int updateStatusByTradeId(@Param("userId") Integer userId, @Param("beforeBalance") Integer beforeBalance, @Param("coinBalance") Integer coinBalance, @Param("lastUpdate") Date lastUpdate, @Param("lastUpdateRemark") String lastUpdateRemark);

    @Update("update gl_fund_user_coin_account set coin_balance = coin_balance+#{amount}, last_update = #{lastUpdate}, last_update_remark = #{lastUpdateRemark} where user_id = #{userId} and coin_balance + #{amount} >= 0 ")
    int updateBalance(@Param("userId") Integer userId, @Param("amount") Integer amount, @Param("lastUpdate") Date lastUpdate, @Param("lastUpdateRemark") String lastUpdateRemark);

}