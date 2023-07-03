package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.RechargeFailureLevelConfig;
import com.seektop.fund.vo.RechargeFailureLevelConfigDO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RechargeFailureLevelConfigMapper extends Mapper<RechargeFailureLevelConfig> {

    @Select("select " +
            "fu.sort_id as sortId, fu.name as levelName, fu.level_id as levelId, fu.level_type as levelType, rflc.new_user_times as newUserTimes, rflc.old_user_times as oldUserTimes," +
            "rflc.target_level_id as targetLevelId, rflc.target_level_name as targetLevelName, rflc.vip_level as vips, rflc.updater as updater, rflc.update_date as updateDate " +
            "from gl_recharge_failure_level_config rflc " +
            "left join gl_fund_userlevel fu on fu.level_id = rflc.level_id " +
            "where rflc.status = 0")
    List<RechargeFailureLevelConfigDO> findConfig();

}