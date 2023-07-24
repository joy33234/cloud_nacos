package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlPaymentMerchantFee;

import java.util.List;

/**
 * 充值商户手续费配置
 */
public interface GlPaymentMerchantFeeMapper extends Mapper<GlPaymentMerchantFee> {

    List<GlPaymentMerchantFee> findByMerchantIds(List<Integer> merchantIds);
}