package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlRechargeSuccessRequest;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 充值补单申请
 */
public interface GlRechargeSuccessRequestMapper extends Mapper<GlRechargeSuccessRequest> {

    @Select("select distinct username from gl_recharge_sucreq")
    List<String> findAllApplicant();


}