package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawCondition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 人工出款条件
 */
public interface GlWithdrawConditionMapper extends Mapper<GlWithdrawCondition> {

    @Update("update gl_withdraw_condition set level_id = #{levelId} where id = #{id}")
    void updateByLevel(@Param("id") Integer id, @Param("levelId") String levelId);
}
