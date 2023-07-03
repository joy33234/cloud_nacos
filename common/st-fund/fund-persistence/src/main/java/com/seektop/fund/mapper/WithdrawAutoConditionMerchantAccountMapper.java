package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;

import java.util.List;

/**
 * 自动出款条件的商户设置(WithdrawAutoConditionMerchantAccount)表数据库访问层
 *
 * @author makejava
 * @since 2021-06-19 14:44:19
 */
public interface WithdrawAutoConditionMerchantAccountMapper extends Mapper<WithdrawAutoConditionMerchantAccount> {

    /**
     * 通过实体作为筛选条件查询
     *
     * @param withdrawAutoConditionMerchantAccount 实例对象
     * @return 对象列表
     */
    List<WithdrawAutoConditionMerchantAccount> queryAll(WithdrawAutoConditionMerchantAccount withdrawAutoConditionMerchantAccount);
}
