package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundUserLevelLock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户锁定层级
 */
public interface GlFundUserLevelLockMapper extends Mapper<GlFundUserLevelLock> {

    List<GlFundUserLevelLock> findByUserIds(List<Integer> userIds);

    @Select("select user_name from gl_fund_userlevellock where level_id=#{levelId} and status=1")
    List<String> findLockUser(@Param("levelId") Integer levelId);

    @Update("update gl_fund_userlevellock set level_id = #{levelId} where user_id = #{userId}")
    void updateUserLevel(@Param("userId") Integer userId, @Param("levelId") Integer levelId);

}