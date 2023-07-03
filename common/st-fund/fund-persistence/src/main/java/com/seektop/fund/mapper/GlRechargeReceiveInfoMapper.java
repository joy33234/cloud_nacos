package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlRechargeReceiveInfo;
import com.seektop.fund.vo.CoinRechargeOrderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface GlRechargeReceiveInfoMapper extends Mapper<GlRechargeReceiveInfo> {

    @Select("select case when count(*) > 0 then 1 else 0 end as isExistTxHash from gl_recharge_receive_info where tx_hash = #{txHash}")
    Boolean isExistTxHash(@Param(value = "txHash") String txHash);

    @Select("select gr.order_id as orderId, gr.user_id as userId, gr.coin, grri.receive_wallet_id as receiveWalletId " +
            "from gl_recharge gr " +
            "left join gl_recharge_receive_info grri on gr.order_id = grri.order_id " +
            "where gr.status = 0 and gr.amount = #{amount} and grri.block_address = #{address} and gr.coin = #{coin} and grri.protocol = #{protocol}")
    CoinRechargeOrderDO getCoinRechargeOrder(@Param(value = "coin") String coin,
                                             @Param(value = "protocol") String protocol,
                                             @Param(value = "address") String address,
                                             @Param(value = "amount") BigDecimal amount);

}