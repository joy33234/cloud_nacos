package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawReturnRequest;
import com.seektop.fund.vo.WithdrawApproveListDO;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 提现操作申请记录
 */
public interface GlWithdrawReturnRequestMapper extends Mapper<GlWithdrawReturnRequest> {

    List<GlWithdrawReturnRequest> findByPage(WithdrawApproveListDO queryDto);

    @Select("select distinct approver from gl_withdraw_returnreq")
    List<String> findAllApprover();

    @Select("select distinct creator from gl_withdraw_returnreq")
    List<String> findAllCreator();

    /**
     * 查询用户部分退回总额
     *
     * @param param
     * @return
     */
    BigDecimal getAmountTotal(Map<String, Object> param);
}