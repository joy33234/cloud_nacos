package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundTransferRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
 * 游戏转账、主播打赏等事件申请记录
 */
public interface GlFundTransferRecordMapper extends Mapper<GlFundTransferRecord> {

  @ResultMap("BaseResultMap")
  @Select("select * from gl_fund_transfer_record where id = #{id} for update")
  GlFundTransferRecord selectForUpdate(@Param("id") String id);
}