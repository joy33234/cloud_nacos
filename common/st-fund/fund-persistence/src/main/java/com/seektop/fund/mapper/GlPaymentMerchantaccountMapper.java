package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import org.apache.ibatis.annotations.Param;

/**
 * 充值商户配置
 */
public interface GlPaymentMerchantaccountMapper extends Mapper<GlPaymentMerchantaccount> {

    Integer getFirstMerchantId(@Param("limitType") Integer limitType);

}