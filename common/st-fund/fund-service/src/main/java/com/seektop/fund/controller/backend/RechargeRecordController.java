package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.rest.Result;
import com.seektop.common.validator.group.CommonValidate;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeErrorBusiness;
import com.seektop.fund.controller.backend.dto.PageInfoExt;
import com.seektop.fund.controller.backend.param.recharge.RechargeErrorDO;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.handler.RechargeRecordHandler;
import com.seektop.fund.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 新财务系统：充值补单、补单审核、充值记录分页查询
 */

@Slf4j
@RestController
@RequestMapping("/manage/fund/recharge/record")
public class RechargeRecordController extends FundBackendBaseController {

    @Resource
    private RechargeRecordHandler rechargeRecordHandler;
    @Autowired
    private GlRechargeErrorBusiness glRechargeErrorBusiness;


    /**
     * 充值补单列表
     */
    @PostMapping(value = "/pending/list", produces = "application/json;charset=utf-8")
    public Result findPendingPageList(RechargePendingQueryDto queryDto) throws GlobalException {
        PageInfo<GlRechargeDO> pageInfo = rechargeRecordHandler.findPendingPageList(queryDto);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 补单审核列表
     */
    @PostMapping(value = "/approve/list", produces = "application/json;charset=utf-8")
    public Result findApprovePageList(@Validated RechargeApproveQueryDto queryDto) throws GlobalException {
        PageInfoExt<RechargeVO> result = rechargeRecordHandler.findApprovePageList(queryDto);
        return Result.genSuccessResult(result);
    }

    /**
     * 充值记录列表
     */
    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result list(RechargeQueryDto queryDto) throws GlobalException {
        GlRechargeCollectResult<GlRechargeDO> resultPageInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        return Result.genSuccessResult(resultPageInfo);
    }

    /**
     * 会员详情-充值记录分页查询
     */
    @PostMapping(value = "/member/list", produces = "application/json;charset=utf-8")
    public Result userList(RechargeQueryDto queryDto) throws GlobalException {
        if (queryDto.getUserId() == null) {
            return Result.genFailResult("用户id不能为空");
        }
        queryDto.setNeedName(1);
        queryDto.setIncludeTotal(true);
        GlRechargeCollectResult<GlRechargeDO> resultPageInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        return Result.genSuccessResult(resultPageInfo);
    }

    /**
     * 代理详情-充值记录
     * @param queryDto
     * @return
     * @throws Exception
     */
    @PostMapping("/recharge/list")
    public Result rechargeList(@Validated(value = CommonValidate.class) RechargeQueryDto queryDto) throws Exception {
        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        queryDto.setDateType(1); //设置为充值时间查询
        queryDto.setNeedName(1);
        queryDto.setIncludeTotal(true);
        GlRechargeCollectResult<GlRechargeDO> resultPageInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        return Result.genSuccessResult(resultPageInfo);
    }

    /**
     * 代理详情-充值记录-导出
     * @param queryDto
     * @return
     */
    @PostMapping("/recharge/list/export")
    public Result rechargeListExport(@Validated(value = CommonValidate.class) RechargeQueryDto queryDto,
                                     @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) {
        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        queryDto.setDateType(1); //设置为充值时间查询
        rechargeRecordHandler.rechargeListExport(queryDto, admin.getUserId());
        return Result.genSuccessResult("正在导出，请稍后下载");
    }

    /**
     * 充值到账金额汇总
     */
    @PostMapping(value = "/list/sum", produces = "application/json;charset=utf-8")
    public Result listSum(RechargeQueryDto queryDto) throws GlobalException {
        List<GlRechargeAllCollect> collect = rechargeRecordHandler.getAllCollect(queryDto);
        return Result.genSuccessResult(collect);
    }

    /**
     * 充值记录导出
     */
    @PostMapping(value = "/list/export", produces = "application/json;charset=utf-8")
    public Result export(RechargeQueryDto queryDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        return Result.genSuccessResult(rechargeRecordHandler.export(queryDto, adminDO));
    }

    /**
     * 充值异常记录
     *
     * @param dto
     * @return
     */
    @PostMapping(value = "/error/list", produces = "application/json;charset=utf-8")
    public Result errorPageList(RechargeErrorDO dto) {
        return Result.genSuccessResult(glRechargeErrorBusiness.findPageList(dto));
    }

    /**
     * 补单审核列表
     */
    @PostMapping(value = "/approve/tips", produces = "application/json;charset=utf-8")
    public Result findApproveTips() throws GlobalException {
        int result = rechargeRecordHandler.findApproveTips();
        return Result.genSuccessResult(result);
    }
}
