package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlPaymentUserCard;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 充值用户曾使用信息(卡号、姓名)
 */
public interface GlPaymentUserCardMapper extends Mapper<GlPaymentUserCard> {

    GlPaymentUserCard findUserCard(@Param("userId") Integer userId, @Param("cardNo") String cardNo);

    GlPaymentUserCard findUserCardByName(@Param("userId") Integer userId, @Param("cardUsername") String cardUsername);

    List<String> findUserCardNoList(@Param("userId") Integer userId);

    List<String> findUserCardNameList(@Param("userId") Integer userId);

    void deleteByUserId(@Param("userId") Integer userId);
}