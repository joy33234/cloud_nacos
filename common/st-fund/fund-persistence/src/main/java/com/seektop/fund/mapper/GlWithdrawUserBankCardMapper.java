package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.vo.UserBindBankDO;
import com.seektop.fund.vo.UserBindQueryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户银行卡记录
 */
public interface GlWithdrawUserBankCardMapper extends Mapper<GlWithdrawUserBankCard> {

    @Select("SELECT * FROM gl_withdraw_userbankcard WHERE user_id = #{userId} AND status = #{status} ORDER BY last_update DESC")
    @ResultType(List.class)
    @ResultMap(value = "BaseResultMap")
    List<GlWithdrawUserBankCard> findUserCards(@Param("userId") Integer userId, @Param("status") Integer status);

    @Select("select * from gl_withdraw_userbankcard where user_id = #{userId} order by last_update desc")
    @ResultType(List.class)
    @ResultMap(value = "BaseResultMap")
    List<GlWithdrawUserBankCard> findUserCardList(Integer userId);

    GlWithdrawUserBankCard findUserCard(@Param("userId") Integer userId, @Param("cardNo") String cardNo);

    List<GlWithdrawUserBankCard> findUserActiveCardList(Integer userId);

    GlWithdrawUserBankCard findCardForWithdrawValid(@Param("userId") Integer userId,
                                                    @Param("status") Integer status,
                                                    @Param("name") String name,
                                                    @Param("cardNo") String cardNo);

    List<UserBindBankDO> bankList(UserBindQueryDO queryDO);
}