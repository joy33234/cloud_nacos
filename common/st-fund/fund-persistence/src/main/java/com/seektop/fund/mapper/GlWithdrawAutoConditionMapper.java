package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdrawAutoCondition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 自动出款条件
 */
public interface GlWithdrawAutoConditionMapper extends Mapper<GlWithdrawAutoCondition> {

    @Update("update gl_withdraw_auto_condition set level_id = #{levelId} where id = #{id}")
    void updateByLevel(@Param("id") Integer id, @Param("levelId") String levelId);

}
