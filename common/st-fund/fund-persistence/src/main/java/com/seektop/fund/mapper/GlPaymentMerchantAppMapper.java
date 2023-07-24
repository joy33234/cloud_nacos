package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlPaymentMerchantApp;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 充值商户应用
 */
public interface GlPaymentMerchantAppMapper extends Mapper<GlPaymentMerchantApp> {

    GlPaymentMerchantApp selectOneByEntity(@Param("paymentId") Integer paymentId,
                                           @Param("merchantId") Integer merchantId,
                                           @Param("useMode") Integer useMode);

    void SyncLimitType(@Param("merchantId") Integer merchantId, @Param("limitType") Integer limitType, @Param("merchantCode") String merchantCode);

    void SyncStatus(@Param("merchantId") Integer merchantId, @Param("status") Integer status);

    void SyncOpenStatus(Map<String, Object> paramMap);

    List<GlPaymentMerchantApp> getActivateMerchant();

    Integer getLevelMerchantCount(@Param("levelId") Integer levelId);

    /**
     * 根据适用方式查询充值商户应用
     * @param useMode
     * @return
     */
    List<GlPaymentMerchantApp> findByUseMode(Integer useMode);
}