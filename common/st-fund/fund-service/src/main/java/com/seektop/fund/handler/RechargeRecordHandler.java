package com.seektop.fund.handler;

import com.github.pagehelper.PageInfo;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.dto.PageInfoExt;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.vo.*;

import java.util.List;


public interface RechargeRecordHandler {

    GlRechargeCollectResult<GlRechargeDO> findRechargeRecordPageList(RechargeQueryDto queryDto) throws GlobalException;

    List<GlRechargeAllCollect> getAllCollect(RechargeQueryDto queryDto) throws GlobalException;

    PageInfo<GlRechargeDO> findPendingPageList(RechargePendingQueryDto queryDto) throws GlobalException;

    PageInfoExt<RechargeVO> findApprovePageList(RechargeApproveQueryDto queryDto) throws GlobalException;

    String export(RechargeQueryDto queryDto, GlAdminDO adminDO) throws GlobalException;

    void rechargeListExport(RechargeQueryDto queryDto, Integer adminId);

    int findApproveTips();
}
