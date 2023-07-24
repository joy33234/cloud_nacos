package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundUserCoinAccountChangeRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户中心钱包
 */
public interface GlFundUserCoinAccountChangeRecordMapper extends Mapper<GlFundUserCoinAccountChangeRecord> {

    @Select("select count(*) from gl_fund_user_coin_account_change_record where user_id = #{userId} and trade_id = #{tradeId}")
    Integer countByTradeId(@Param("userId") Integer userId, @Param("tradeId") String tradeId);


}