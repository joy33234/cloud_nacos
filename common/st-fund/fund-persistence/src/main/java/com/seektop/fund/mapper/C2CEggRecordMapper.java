package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.C2CEggRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

public interface C2CEggRecordMapper extends Mapper<C2CEggRecord> {

    @Select("select case when count(id) > 0 then 1 else 0 end as isAccord from gl_c2c_egg_record where type = #{type} and status = 0 and start_date < #{date} and end_date > #{date}")
    Boolean isAccord(@Param(value = "date") Date date, @Param(value = "type") Short type);

}