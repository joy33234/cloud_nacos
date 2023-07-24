package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface C2CWithdrawOrderPoolMapper extends Mapper<C2CWithdrawOrderPool> {

    List<C2CWithdrawOrderPool> matchWithdrawOrder(@Param(value = "amount") BigDecimal amount,
                                                  @Param(value = "userId") Integer userId,
                                                  @Param(value = "ip") String ip,
                                                  @Param(value = "sameIpCanMatch") Boolean sameIpCanMatch);

    List<C2CWithdrawOrderPool> matchWithdrawOrderByLessThan(@Param(value = "amount") BigDecimal amount,
                                                            @Param(value = "userId") Integer userId,
                                                            @Param(value = "ip") String ip,
                                                            @Param(value = "sameIpCanMatch") Boolean sameIpCanMatch);

}