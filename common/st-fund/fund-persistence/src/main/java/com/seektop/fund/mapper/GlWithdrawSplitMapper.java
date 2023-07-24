package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawSplit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 提现拆单
 *
 * @author darren 2019-1-8
 */
public interface GlWithdrawSplitMapper extends Mapper<GlWithdrawSplit> {

    @Select("SELECT order_id AS orderId,parent_id AS parentId FROM gl_withdraw_split " +
            "WHERE create_time between #{startDate} AND #{endDate} ORDER BY parent_id")
    List<GlWithdrawSplit> findWithdrawSplitByTime(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Select("SELECT t.order_id AS orderId,t.parent_id AS parentId,t.amount AS amount " +
            "FROM gl_withdraw_split t LEFT JOIN gl_withdraw_split t1 ON t.parent_id = t1.parent_id WHERE t1.order_id = #{orderId}")
    List<GlWithdrawSplit> findAllSplitOrderByOrderId(@Param("orderId") String orderId);
}
