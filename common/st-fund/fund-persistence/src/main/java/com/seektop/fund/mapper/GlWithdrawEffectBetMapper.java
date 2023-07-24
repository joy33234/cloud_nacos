package com.seektop.fund.mapper;


import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawEffectBet;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户提现流水信息
 */
public interface GlWithdrawEffectBetMapper extends Mapper<GlWithdrawEffectBet> {

    @Select("select * from gl_withdraw_effect_bet where user_id = #{userId} and coin = #{coin}")
    @ResultMap(value = "BaseResultMap")
    GlWithdrawEffectBet findOne(@Param(value = "userId") Integer userId,
                                      @Param(value = "coin") String coin);

    @Select("select * from gl_withdraw_effect_bet where user_id = #{userId}")
    @ResultMap(value = "BaseResultMap")
    @ResultType(List.class)
    List<GlWithdrawEffectBet> findByUserId(@Param(value = "userId") Integer userId);



    @Update("update gl_withdraw_effect_bet set lose = #{lose} where user_id = #{userId} and coin = #{coin}")
    void resetLose(@Param(value = "userId") Integer userId,
                           @Param(value = "coin") String coin,
                           @Param(value = "lose") Boolean lose);

}
