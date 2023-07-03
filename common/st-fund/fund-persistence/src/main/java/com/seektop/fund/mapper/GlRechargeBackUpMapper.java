package com.seektop.fund.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface GlRechargeBackUpMapper {

    void createTable(@Param("shard") String shard);

    int insert(@Param("shard") String shard, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    String checkTable(@Param("shard") String shard, @Param("schema") String schema);

    String getMaxRechargeDate(@Param("shard") String shard);
}
