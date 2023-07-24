package com.seektop.fund.handler;

import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.WithdrawExceptionBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawListResult;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.controller.backend.result.ApproveResult;
import com.seektop.fund.dto.param.withdraw.RiskApproveDto;
import com.seektop.fund.handler.validation.AuthExceptionMenuValidation;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.vo.WithdrawExceptionQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import com.seektop.system.service.GlSystemDepartmentJobService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WithdrawExceptionHandler {

    @Reference(retries = 1, timeout = 3000)
    private GlSystemDepartmentJobService systemDepartmentJobService;
    @Reference(retries = 1, timeout = 5000)
    private GlUserService userService;
    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Resource
    private WithdrawExceptionBusiness withdrawExceptionBusiness;
    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @Autowired
    private RedisService redisService;
    @Autowired
    private CheckLabelHandler checkLabelHandler;

    /**
     * 风控提款审核，待审核，审核搁置，全部列表
     *
     * @param queryDto
     * @param admin
     * @return
     * @throws GlobalException
     */
    public PageInfo<GlWithdrawListResult> findList(WithdrawExceptionQueryDto queryDto, GlAdminDO admin) throws GlobalException {
        PageInfo<GlWithdrawListResult> resultPageInfo = new PageInfo<>();
        resultPageInfo.setPageNum(queryDto.getPage());
        resultPageInfo.setPageSize(queryDto.getSize());
        resultPageInfo.setTotal(0);
        resultPageInfo.setList(Lists.newArrayList());

        boolean isEmpty = setParams(queryDto, admin);
        if (isEmpty) {
            return resultPageInfo;
        }
        if (queryDto.getUserTypes() != null && queryDto.getUserTypes().size() == 2 && queryDto.getUserTypes().contains(UserConstant.UserType.PROXY)) {
            throw new GlobalException("不支持该帐户类型查询条件");
        }
        resultPageInfo = withdrawExceptionBusiness.findWithdrawExceptionList(queryDto);
        return resultPageInfo;
    }

    /**
     * 风控提款审核，待审核，审核搁置，全部列表导出
     *
     * @param queryDto
     * @param admin
     * @throws GlobalException
     */
    public void download(WithdrawExceptionQueryDto queryDto, GlAdminDO admin) throws GlobalException {
        // 限制导出
        String lockKey = "EXCEPTION_DOWNLOAD_LOCK_" + admin.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            throw new GlobalException("5分钟只能导出一次");
        }
        redisService.set(lockKey, "1", 300);
        boolean isEmpty = setParams(queryDto, admin);
        if (isEmpty) {
            throw new GlobalException("数据为空无法下载");
        }

        // 异步导出到文件
        try {
            withdrawExceptionBusiness.download(queryDto, admin);
        } catch (Exception e) {
            log.error("withdrawUserRiskBusiness.downLoadExceptionList error", e);
        }
    }

    public ApproveResult approve(WithdrawExceptionApproveDto approveDto, GlAdminDO admin) throws GlobalException {
        // 检查管理员的数据权限
        Validator.build().add(new AuthExceptionMenuValidation(admin, approveDto.getSystemId(),
                systemDepartmentJobService)).valid();

        // 用户操作后释放查看用户缓存
        Integer status = approveDto.getStatus();
        List<String> orderIds = approveDto.getOrderId();
        if (status == 5) { //取消不操作
            orderIds.forEach(orderId -> {
                String key = String.format("%s%s", RedisKeyHelper.EXCEPTION_WITHDRAW_CACHE, orderId);
                Map<String, Long> result = redisService.get(key, Map.class);
                if (!ObjectUtils.isEmpty(result)) {
                    result.remove(admin.getUsername());
                    redisService.set(key, result, 600);
                }
            });
            return new ApproveResult(orderIds.size());
        }

        List<GlWithdraw> glWithdrawList = new ArrayList<>();
        approveDto.setUpdateTime(new Date());
        List<Integer> statusList = Lists.newArrayList(FundConstant.WithdrawStatus.RISK_PENDING, FundConstant.WithdrawStatus.REVIEW_HOLD);
        List<GlWithdraw> withdrawList = glWithdrawBusiness.findByOrderIds(orderIds);
        for (String orderId : orderIds) {
            Optional<GlWithdraw> first = withdrawList.stream().filter(w -> orderId.equals(w.getOrderId())).findFirst();
            if (!first.isPresent()) {
                throw new GlobalException(ResultCode.DATA_ERROR, String.format("订单[%s]异常提现记录不存在", orderId));
            }
            GlWithdraw dbWithdraw = first.get();
            if (statusList.stream().noneMatch(s -> s.equals(dbWithdraw.getStatus()))) {
                throw new GlobalException(ResultCode.DATA_ERROR, String.format("订单[%s]异常提现记录已审核", orderId));
            }
            if (2 == status && StringUtils.isBlank(approveDto.getRemark())) {
                throw new GlobalException(ResultCode.DATA_ERROR, String.format("订单[%s]异常提现备注不能为空", orderId));
            }
            GlWithdraw withdraw = withdrawExceptionBusiness.doWithdrawRiskApprove(dbWithdraw, approveDto, admin);
            glWithdrawList.add(withdraw);
        }
        try {
            // 按事务提交数据
            withdrawExceptionBusiness.doWithdrawRiskApprove(glWithdrawList, approveDto, admin);

            //发送自动出款消息
            for (GlWithdraw withdraw : glWithdrawList) {
                glWithdrawBusiness.sendWithdrawMsg(withdraw);
            }
            // 保存标记
            checkLabelHandler.saveCheckLabel(glWithdrawList.get(0), approveDto, admin);
            // 用户操作后释放查看用户缓存
            orderIds.forEach(orderId -> {
                String key = String.format("%s%s", RedisKeyHelper.EXCEPTION_WITHDRAW_CACHE, orderId);
                redisService.delete(key);
            });
        }
        catch (GlobalException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("withdrawExceptionBusiness.doWithdrawRiskApprove error", e);
            throw new GlobalException("订单审核失败");
        }
        return new ApproveResult(withdrawList.size());
    }

    public void approve(RiskApproveDto dto) throws GlobalException {
        //锁定审核通过，则将查询出来所有待审核、待搁置的订单
        List<Integer> statusList = Lists.newArrayList(-3, -4);
        List<WithdrawVO> list = Lists.newArrayList();

        WithdrawExceptionQueryDto queryDto = new WithdrawExceptionQueryDto();
        queryDto.setUserType(dto.getUserType());
        queryDto.setClientType(-1);
        queryDto.setUserIds(Lists.newArrayList(dto.getUserId()));
        statusList.forEach(s -> {
            queryDto.setStatus(s);
            //查询会员或代理
            List<WithdrawVO> results = glWithdrawMapper.findWithdrawExceptionList(queryDto);
            if(!CollectionUtils.isEmpty(results)) {
                list.addAll(results);
            }
        });

        List<GlWithdraw> glWithdrawList = new ArrayList<>();
        if (list.size() > 0) {
            WithdrawExceptionApproveDto approveDto = new WithdrawExceptionApproveDto();
            approveDto.setStatus(dto.getStatus());
            approveDto.setRemark(dto.getRemark());
            approveDto.setRejectReason(dto.getRejectReason());
            approveDto.setUpdateTime(new Date());
            GlAdminDO adminDO = new GlAdminDO();
            adminDO.setUserId(dto.getOperatorUserId());
            adminDO.setUsername(dto.getOperator());
            for (GlWithdraw glWithdraw : list) {//该会员的所有审核取款拒绝
                glWithdrawList.add(withdrawExceptionBusiness.doWithdrawRiskApprove(glWithdraw, approveDto, adminDO));
            }
            //风险审核拒绝操作流水
            withdrawExceptionBusiness.doWithdrawRiskApprove(glWithdrawList, approveDto, adminDO);
        }
    }

    /**
     * 设置参数
     *
     * @param queryDto
     * @param admin
     * @return 是否无数据
     * @throws GlobalException
     */
    private boolean setParams(WithdrawExceptionQueryDto queryDto, GlAdminDO admin) throws GlobalException {
        // 检查管理员的数据权限
        Validator.build().add(new AuthExceptionMenuValidation(admin, queryDto.getSystemId(),
                systemDepartmentJobService)).valid();

        /**
         * 按照姓名&手机号
         */
        List<Integer> userIds = new ArrayList<>();
        Integer searchType = queryDto.getSearchType();
        String keywords = queryDto.getKeywords();
        if (StringUtils.isNotBlank(keywords)) {
            if (searchType == 4 || searchType == 5) {
                List<GlUserDO> users = null;
                if (searchType == 4) {
                    users = RPCResponseUtils.getData(userService.findByReallyName(keywords));
                } else if (searchType == 5) {
                    users = RPCResponseUtils.getData(userService.findByTelephone(keywords));
                }
                if (!CollectionUtils.isEmpty(users)) {
                    userIds = users.stream().map(GlUserDO::getId).collect(Collectors.toList());
                } else {
                    return true;
                }
            } else if (searchType == 2) {
                GlUserDO glUser = RPCResponseUtils.getData(userService.getUserInfoByUsername(keywords));
                if (null == glUser) {
                    return true;
                }
                userIds.add(glUser.getId());
            }
        }

        List<Integer> list = getUserId(queryDto.getNameBatch());
        if (!CollectionUtils.isEmpty(list)) {
            userIds.addAll(list);
        }
        if (queryDto.getUserTypes() != null && !queryDto.getUserTypes().isEmpty()) {
            queryDto.setUserType(null);
        }

        queryDto.setUserIds(userIds);
        return false;
    }

    //封装用户名批量查询
    private List<Integer> getUserId(String nameBatch) throws GlobalException {
        if (StringUtils.isBlank(nameBatch)) {
            return com.google.common.collect.Lists.newArrayList();
        }
        List<String> nameList = Arrays.asList(nameBatch.split(","));
        List<GlUserDO> listUser = RPCResponseUtils.getData(userService.findByUsernames(nameList));
        return listUser.stream().map(GlUserDO::getId).collect(Collectors.toList());
    }
}
