package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundMerchantWithdraw;
import com.seektop.fund.vo.WithdrawMerchantBalanceResult;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 三方出款商户余额
 */
public interface GlFundMerchantWithdrawMapper extends Mapper<GlFundMerchantWithdraw> {


    BigDecimal getTotalBalance();

    List<WithdrawMerchantBalanceResult> findWithdrawMerchantBalanceList(
            @Param("channelId") Integer channelId, @Param("merchantCode") String merchantCode, @Param("status") Integer status, @Param("coin") String coin);

}