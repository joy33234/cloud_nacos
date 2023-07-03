package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.WithdrawUserBankCardApply;

import java.util.List;
import java.util.Map;

/**
 * 人工绑卡申请
 */
public interface WithdrawUserBankCardApplyMapper extends Mapper<WithdrawUserBankCardApply> {

    int insertApply(WithdrawUserBankCardApply apply);

    List<WithdrawUserBankCardApply> findBy(Map<String, Object> params);
}
