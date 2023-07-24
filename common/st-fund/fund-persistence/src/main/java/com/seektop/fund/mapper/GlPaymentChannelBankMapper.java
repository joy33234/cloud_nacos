package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlPaymentChannelBank;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 充值渠道银行
 */
public interface GlPaymentChannelBankMapper extends Mapper<GlPaymentChannelBank> {

    @Results(id = "glWithdrawUserBankCardResult", value = {
            @Result(property = "bankId", column = "bank_id", id = true),
            @Result(property = "bankCode", column = "bank_code"),
            @Result(property = "bankName", column = "bank_name")
    })
    @Select("select * from gl_payment_channelbank where status = 0 and channel_id = #{channelId} order by sort")
    List<GlPaymentChannelBank> getChannelBank(@Param("channelId") final Integer channelId);

    /**
     * 根据channelIds查询
     * @param channelIds
     * @return
     */
    List<GlPaymentChannelBank> findByChannelIds(List<Integer> channelIds);

}