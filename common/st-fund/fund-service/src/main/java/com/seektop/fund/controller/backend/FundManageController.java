package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.FundRequestBusiness;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.business.GlFundChangeRequestBusiness;
import com.seektop.fund.controller.backend.dto.RechargeRequestExportDto;
import com.seektop.fund.controller.backend.param.recharge.FundRequestAddDto;
import com.seektop.fund.controller.backend.param.recharge.FundRequestDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.system.dto.FundAdjustmentDO;
import com.seektop.system.service.GlSystemDepartmentJobService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/manage/fund")
public class FundManageController extends FundBackendBaseController {
    @Resource
    private RedisService redisService;
    @Resource
    private GlFundChangeRequestBusiness glFundChangeRequestBusiness;
    @Resource
    private GlFundBusiness glFundBusiness;
    @Resource
    private FundRequestBusiness fundRequestBusiness;
    @Resource
    private DynamicKey dynamicKey;
    @Reference(retries = 2, timeout = 3000)
    private GlSystemDepartmentJobService glSystemDepartmentJobService;


    @PostMapping(value = "/export/operation", produces = "application/json;charset=utf-8")
    public Result exportOperation(RechargeRequestExportDto exportDto,
                                  @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        log.info("exportDto = {}", exportDto);
        PageInfo<GlFundChangeRequest> pageInfo = new PageInfo<>();
        if (CollectionUtils.isEmpty(exportDto.getChangeType())
                || (exportDto.getChangeType().size() >= 2 && exportDto.getChangeType().contains(FundConstant.ChangeOperateType.REDUCE))) {
            return Result.genSuccessResult(pageInfo);
        }
        if (ObjectUtils.isEmpty(exportDto.getSubType())) {
            return Result.genSuccessResult(pageInfo);
        }
        exportDto.setUserId(adminDO.getUserId());
        try {
            glFundChangeRequestBusiness.newFundChangeDownload(exportDto);
            Result result = Result.genSuccessResult("正在下载，请在下载列表查看(一次最多导出20W数据)");
            result.setKeyConfig(FundLanguageMvcEnum.DOWNLOADING_MAX_20W);
            return Result.genSuccessResult("正在下载，请在下载列表查看(一次最多导出20W数据)");
        } catch (GlobalException e) {
            return Result.genFailResult(e.getMessage());
        }
    }

    @PostMapping(value = "/list/operation", produces = "application/json;charset=utf-8")
    public Result listOperation(RechargeRequestExportDto exportDto,
                                @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        log.info("exportDto = {}", exportDto);
        PageInfo<GlFundChangeRequest> pageInfo = new PageInfo<>();
        if (CollectionUtils.isEmpty(exportDto.getChangeType())
            || (exportDto.getChangeType().size() >= 2 && exportDto.getChangeType().contains(FundConstant.ChangeOperateType.REDUCE))) {  //查询全部调整类型
            if(CollectionUtils.isEmpty(pageInfo.getList())){
                pageInfo.setList(Lists.newArrayList());
            }
            return Result.genSuccessResult(pageInfo);
        }
        if (ObjectUtils.isEmpty(exportDto.getSubType())) {
            if(CollectionUtils.isEmpty(pageInfo.getList())){
                pageInfo.setList(Lists.newArrayList());
            }
            return Result.genSuccessResult(pageInfo);
        }
        pageInfo = glFundChangeRequestBusiness.findChangeRequestList(exportDto);
        if(CollectionUtils.isEmpty(pageInfo.getList())){
            pageInfo.setList(Lists.newArrayList());
        }
        return Result.genSuccessResult(pageInfo);
    }

//    /**
//     * 资金调整申请
//     * 步骤
//     *  1.限制调整金额上限
//     *  2.检查用户类型和操作类型
//     *  3.虚拟账号不能进行相关操作
//     * @param fundRequestAddDto
//     * @param adminDO
//     * @return
//     * @throws GlobalException
//     */
//    @PostMapping(value = "/add", produces = "application/json;charset=utf-8")
//    public Result addRequest(@Validated FundRequestAddDto fundRequestAddDto,
//                             @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
//        FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(), fundRequestAddDto.getChangeType(), fundRequestAddDto.getSubType());
//        if(changeOperateSubType == null){
//            log.error("appName:{} param = {}", dynamicKey.getAppName(), fundRequestAddDto);
//            return Result.genFailResult("子类型不存在");
//        }
//        //参数校验
//        fundRequestBusiness.validateRequestAmount(changeOperateSubType, fundRequestAddDto);
//
//        //校验结果容器
//        FundRequestDO doResults = new FundRequestDO();
//        List<GlFundChangeRequest> requestList = fundRequestBusiness.checkType(fundRequestAddDto, adminDO, changeOperateSubType, doResults);
//        if (doResults.getInvalid().size() != 0 || doResults.getFailList().size() != 0) {
//            return Result.genSuccessResult(doResults);
//        }
//        if (requestList.size() != 0) {
//            glFundBusiness.doFundChargeSubmit(requestList, fundRequestAddDto);
//        }
//        return Result.genSuccessResult(doResults);
//    }

    @PostMapping(value = "/list/creator", produces = "application/json;charset=utf-8")
    public Result listCreator() {
        return Result.genSuccessResult(glFundChangeRequestBusiness.findAllCreator());
    }

    @PostMapping(value = "/list/first", produces = "application/json;charset=utf-8")
    public Result listFirst() {
        return Result.genSuccessResult(glFundChangeRequestBusiness.findAllFirstApprover());
    }

    @PostMapping(value = "/list/second", produces = "application/json;charset=utf-8")
    public Result listSecond() {
        return Result.genSuccessResult(glFundChangeRequestBusiness.findAllSecondApprover());
    }

//    /**
//     * 资金调整 一审（批量一审）
//     * 校验和更新状态 基本无业务逻辑
//     * @param orderIds
//     * @param status
//     * @param remark
//     * @param adminDO
//     * @return
//     * @throws GlobalException
//     */
//    @PostMapping(value = "/approve/first", produces = "application/json;charset=utf-8")
//    public Result approveFirst(@RequestParam List<String> orderIds,
//                               @RequestParam Integer status,
//                               @RequestParam String remark,
//                               @RequestHeader(defaultValue = "50") Integer systemId,
//                               @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
//        if (orderIds.isEmpty())
//            return Result.genFailResult("资金调整申请orderId不能为空");
//        if (orderIds.size() > 500)
//            return Result.genFailResult("批量审核数据不能超过500条");
//
//        String idsStr = "'" + StringUtils.join(orderIds, "','") + "'";
//        List<GlFundChangeRequest> requests = glFundChangeRequestBusiness.findByIds(idsStr);
//        if (requests.size() != orderIds.size())
//            return Result.genFailResult("资金调整申请不存在");
//
//        List<Integer> subTypeList = Lists.newArrayList();//操作子类型
//        for (GlFundChangeRequest dbReq : requests) {
//            if (null == dbReq || dbReq.getStatus() != FundConstant.ChangeReqStatus.PENDING_APPROVAL)
//                return Result.genFailResult("资金调整申请已审核");
//            if (!this.checkSubType(dbReq, subTypeList))
//                return Result.genFailResult("所有资金调整申请操的作子类型必须相同");
//        }
//        Integer changeType = requests.get(0).getChangeType();
//        Integer subType = requests.get(0).getSubType();
//        boolean checkDataMenu = checkDataMenu(systemId, adminDO.getJobId(), changeType, subType, 1);
//        if (!checkDataMenu) {
//            return Result.genFailResult("您没有数据权限，请先去授权");
//        }
//
//        Date now = new Date();
//        GlFundChangeRequest request = new GlFundChangeRequest();
//        request.setFirstApprover(adminDO.getUsername());
//        request.setFirstRemark(remark);
//        request.setFirstTime(now);
//        request.setStatus(
//                status == FundConstant.ChangeReqStatus.FIRST_APPROVAL_ALLOW ?
//                FundConstant.ChangeReqStatus.FIRST_APPROVAL_ALLOW :
//                FundConstant.ChangeReqStatus.FIRST_APPROVAL_DENY);
//        glFundChangeRequestBusiness.doFundFirstApprove(idsStr, request, requests);
//        return Result.genSuccessResult();
//    }

//    /**
//     * 资金调整二审
//     * 步骤：
//     *  1.校验
//     *  2.调用二审逻辑
//     * @param orderIds
//     * @param status
//     * @param remark
//     * @param adminDO
//     * @return
//     * @throws GlobalException
//     */
//    @PostMapping(value = "/approve/second", produces = "application/json;charset=utf-8")
//    public Result approveSecond(@RequestParam List<String> orderIds, @RequestParam Integer status,
//                                @RequestParam String remark,
//                                @RequestHeader(defaultValue = "50") Integer systemId,
//                                @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
//        if (orderIds.isEmpty())
//            return Result.genFailResult("资金调整申请orderId不能为空");
//        if (orderIds.size() > 500)
//            return Result.genFailResult("批量审核数据不能超过500条");
//        String idsStr = "'" + StringUtils.join(orderIds, "','") + "'";
//        List<GlFundChangeRequest> requests = glFundChangeRequestBusiness.findByIds(idsStr);
//        if (requests.size() != orderIds.size())
//            return Result.genFailResult("资金调整申请不存在");
//
//        List<Integer> subTypeList = Lists.newArrayList();//操作子类型
//        for (GlFundChangeRequest dbReq : requests) {
//            if (null == dbReq || dbReq.getStatus() != FundConstant.ChangeReqStatus.FIRST_APPROVAL_ALLOW)
//                return Result.genFailResult("资金调整一审未通过");
//
//            if (!this.checkSubType(dbReq, subTypeList))
//                return Result.genFailResult("所有资金调整申请操的作子类型必须相同");
//        }
//        Integer changeType = requests.get(0).getChangeType();
//        Integer subType = requests.get(0).getSubType();
//        boolean checkDataMenu = checkDataMenu(systemId, adminDO.getJobId(), changeType, subType, 2);
//        if (!checkDataMenu) {
//            return Result.genFailResult("您没有数据权限，请先去授权");
//        }
//
//        glFundBusiness.doFundSecondApprove(requests, status, remark, adminDO.getUsername());
//        return Result.genSuccessResult();
//    }

    /**
     * 批量操作调整资金审核-检查所选申请是否为同一子操作类型
     *
     * @param request
     * @param subTypes
     * @return
     */
    private boolean checkSubType(GlFundChangeRequest request, List<Integer> subTypes) {
        if (request == null || request.getSubType() == null || subTypes == null)
            return false;
        if (subTypes.isEmpty() || (subTypes.get(0).intValue() != request.getSubType().intValue())) {
            subTypes.add(request.getSubType());
        }
        return subTypes.size() > 1 ? false : true;
    }

    /**
     * 检查数据权限
     *
     * @param adminJobId
     * @param subType
     * @param check
     * @return
     */
    private boolean checkDataMenu(Integer systemId, Integer adminJobId, Integer changeType, Integer subType, Integer check) throws GlobalException {
        FundAdjustmentDO fundAdjustmentDO = new FundAdjustmentDO();
        fundAdjustmentDO.setChangeType(changeType);
        fundAdjustmentDO.setSubType(subType);
        fundAdjustmentDO.setCheck(check);
        fundAdjustmentDO.setJobId(adminJobId);
        fundAdjustmentDO.setSystemId(systemId);
        try {
            return RPCResponseUtils.getData(glSystemDepartmentJobService.checkFundDataMenu(fundAdjustmentDO));
        }catch (Exception e){
            log.error(e.getMessage(), e);
            return false;
        }
    }
}


