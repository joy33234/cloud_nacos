package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawBank;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 出款支持银行
 */
public interface GlWithdrawBankMapper extends Mapper<GlWithdrawBank> {

    @Select("select bank_id from gl_withdraw_bank where bank_name=#{bank_name} ")
    Integer getBankIdByName(@Param("bank_name") String bank_name);
}