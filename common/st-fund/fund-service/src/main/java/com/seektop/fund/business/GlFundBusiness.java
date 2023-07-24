package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.seektop.activity.dto.param.changeReqActivity.GlFundChangeRequestActivityDO;
import com.seektop.activity.dto.result.GlActivityProxyDO;
import com.seektop.activity.dto.result.GlFundChangeRequestActivityResult;
import com.seektop.activity.service.GlActivityProxyService;
import com.seektop.activity.service.GlFundChangeReqActivityService;
import com.seektop.agent.dto.ValidWithdrawalDto;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.GameOrderPrefix;
import com.seektop.common.utils.RegexValidator;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.service.BettingService;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.MsgEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.BettingBalanceEnum;
import com.seektop.enumerate.fund.NameTypeEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;
import com.seektop.fund.controller.backend.param.recharge.FundRequestAddDto;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.handler.FundChangeToolHandler;
import com.seektop.fund.handler.GlFundReportHandler;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.fund.handler.ReportExtendHandler;
import com.seektop.fund.mapper.FundProxyAccountMapper;
import com.seektop.fund.mapper.GlFundChangeApproveMapper;
import com.seektop.fund.mapper.GlFundChangeRelationMapper;
import com.seektop.fund.mapper.GlFundChangeRequestMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.service.FundProxyAccountService;
import com.seektop.report.common.BonusReport;
import com.seektop.report.fund.BettingBalanceReport;
import com.seektop.report.fund.SubCoinReport;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;


@Slf4j
@Component
public class GlFundBusiness {

    @DubboReference(retries = 2, timeout = 5000)
    private GlUserService glUserService;

    @DubboReference(retries = 2, timeout = 5000)
    private FundProxyAccountService fundProxyAccountService;

    @DubboReference(retries = 2, timeout = 5000)
    private BettingService bettingService;

    @DubboReference(retries = 2, timeout = 5000)
    private GlActivityProxyService activityProxyService;

    @DubboReference(retries = 2, timeout = 5000)
    private GlFundChangeReqActivityService fundChangeReqActivityService;

    @Resource(name = "moneyChangeNoticeHandler")
    private NoticeHandler noticeHandler;

    @Resource
    private GlFundChangeRequestMapper glFundChangeRequestMapper;

    @Resource
    private GlFundChangeRelationMapper glFundChangeRelationMapper;

    @Resource
    private GlFundChangeApproveMapper glFundChangeApproveMapper;

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Resource
    private FundProxyAccountMapper fundProxyAccountMapper;

    @Resource
    private ReportService reportService;

    @Resource
    private RedisService redisService;

    @Resource
    private GlFundReportHandler glFundReportHandler;

    @Resource
    private ReportExtendHandler reportExtendHandler;

    @Resource
    private DynamicKey dynamicKey;

    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;
    @Autowired
    private FundChangeToolHandler fundChangeToolHandler;
    @Autowired
    private GlFundChangeRequestBusiness fundChangeRequestBusiness;
    @Autowired
    private FundProxyAccountBusiness fundProxyAccountBusiness;


    /**
     * 步骤：
     * 1.添加申请记录
     * 2.添加关联订单记录
     * 3.如果是减币，先扣钱
     *
     * @param requestList
     * @param fundRequestAddDto
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doFundChargeSubmit(List<GlFundChangeRequest> requestList, FundRequestAddDto fundRequestAddDto) throws GlobalException {
        try {
            for (GlFundChangeRequest request : requestList) {
                //代理活动上报红利
                if (request.getSubType() == FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()) {
                    beforeInsert(request);
                }
                glFundChangeRequestMapper.insert(request);
                //如果是资金调整减币，先扣钱
                doSubCoinIfNeed(request);
                // 绑定关联的订单号
                GlFundChangeRelation relation = new GlFundChangeRelation();
                relation.setOrderId(request.getOrderId());
                relation.setRelationRechargeOrderId(fundRequestAddDto.getRelationOrderId());
                relation.setThirdOrderId(fundRequestAddDto.getThirdOrderId());

                glFundChangeRelationMapper.insert(relation);
            }
        } catch (Exception e) {
            log.info(("doFundChargeSubmit error"), e);
            throw new GlobalException("系统繁忙,请稍后重试");
        }
    }

    /**
     * 步骤：
     * 1.添加申请记录
     * 2.添加关联订单记录
     * 3.如果是减币，先扣钱
     *
     * @param requestList
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doFundChargeSubmit(List<GlFundChangeRequest> requestList) throws GlobalException {
        try {
            for (GlFundChangeRequest request : requestList) {
                glFundChangeRequestMapper.insert(request);
                //如果是资金调整减币，先扣钱
                doSubCoinIfNeed(request);
                // 绑定关联的订单号
                GlFundChangeRelation relation = new GlFundChangeRelation();
                relation.setOrderId(request.getOrderId());
                relation.setRelationRechargeOrderId(request.getRelationOrderId());
                glFundChangeRelationMapper.insert(relation);
            }
        } catch (Exception e) {
            log.info(("doFundChargeSubmit error"), e);
            throw new GlobalException("系统繁忙,请稍后重试");
        }
    }

//    @Transactional(rollbackFor = {GlobalException.class})
//    public void doFundSecondApprove(List<GlFundChangeRequest> changeRequests, Integer status,
//                                    String remark, String approverName) throws GlobalException {
//
//        try {
//            for (GlFundChangeRequest request : changeRequests) {
//                GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(request.getUserId()));
//                fundSecondApprove(request, glUser, status, remark, approverName);
//            }
//        } catch (GlobalException e) {
//            log.error("doFundSecondApprove_error:{}", e);
//            throw new GlobalException(e.getMessage(), e);
//        }
//    }

    /**
     * 对减币操作进行账变 和 上报
     */
    private void doSubCoinIfNeed(GlFundChangeRequest changeRequest) throws GlobalException {

        log.info("doSubCoinIfNeed params = {}", changeRequest);
        if (!fundChangeToolHandler.isSub(changeRequest)) { // 不是减币处理
            return;
        }
        BigDecimal amount = changeRequest.getAmount();
        changeRequest.setAmount(amount.compareTo(BigDecimal.ZERO) > 0 ? amount.negate() : amount);
        //账变
        glFundUserAccountBusiness.addBalance(changeRequest.getUserId(), changeRequest.getAmount(), DigitalCoinEnum.CNY);

        //减币上报
        GlUserDO user = RPCResponseUtils.getData(glUserService.findById(changeRequest.getUserId()));
        glFundReportHandler.reportSubCoin(changeRequest, user, 0);
    }

//    /**
//     * 二次审核
//     * 步骤：
//     * 1.常规 修改申请表记录
//     * 2.新增审核表记录
//     * 3.对各个业务进行处理
//     * 4.审核不通过，直接返回
//     * 5.审核通过
//     * 账变
//     * 减币逻辑/加币逻辑（代理操作：代理和会员账号之间的平衡）
//     * 上报
//     * 流水需求
//     *
//     * @param changeRequest
//     * @param user
//     * @param status
//     * @param remark
//     * @param approverName
//     * @throws GlobalException
//     */
//    private void fundSecondApprove(GlFundChangeRequest changeRequest, GlUserDO user, Integer status, String remark,
//                                   String approverName) throws GlobalException {
//
//        if (status == 3) {  //搁置
//            return;  //搁置还是属于待审核的状态不进入审核通过记录表
//        }
//        try {
//            Date now = new Date();
//            GlFundChangeRequest request = glFundChangeRequestMapper.selectByPrimaryKey(changeRequest.getOrderId());
//            //兼容老数据 subType 可能为null
//            Integer subType = changeRequest.getSubType();
//            request.setSecondApprover(approverName);
//            request.setSecondRemark(remark);
//            request.setSecondTime(now);
//            if (status == 1) {  //通过
//                request.setStatus(3);  //二审通过
//                //代理活动上报红利
//                if (request.getSubType() == FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()) {
//                    GlFundChangeRequestActivityDO activityDO = GlFundChangeRequestActivityDO.builder()
//                            .status(ProjectConstant.Status.SUCCESS)
//                            .orderId(request.getOrderId())
//                            .updateTime(new Date())
//                            .build();
//
//                    RPCResponse<GlFundChangeRequestActivityResult> saveResponse = fundChangeReqActivityService.update(activityDO);
//                    if (RPCResponseUtils.isFail(saveResponse) || ObjectUtils.isEmpty(saveResponse.getData())) {
//                        throw new GlobalException("保存资金调整代理活动失败");
//                    }
//                    request.setActId(saveResponse.getData().getActId());
//                    fundAdjustReport(request, user, ProjectConstant.Status.SUCCESS);
//                }
//            } else if (status == 2) { //拒绝
//                request.setStatus(4); //二审拒绝
//            }
//            if (status == 3) {  //搁置
//                request.setStatus(5);  //二审搁置
//            }
//            glFundChangeRequestMapper.updateByPrimaryKeySelective(request);
//            GlFundChangeApprove approve = new GlFundChangeApprove();
//            approve.setCreateTime(now);
//            approve.setCreator(approverName);
//            approve.setOrderId(changeRequest.getOrderId());
//            approve.setRemark(remark);
//            approve.setStatus(status);
//            glFundChangeApproveMapper.insertSelective(approve);
//
//            // 二审未通过，处理结束
//            if (status == 2) {
//                //不通过的减币操作,用户加钱，添加减币退回上报
//                fundChangeRequestBusiness.doSubCoinRecoverIfNeed(changeRequest, user);
//                // 处理红利上报的状态
//                if (request.getSubType() == FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()) {
//                    glFundReportHandler.reportBonusStatus(request.getOrderId(), ProjectConstant.Status.FAILED);
//                }
//                return;
//            }
//            boolean isSub = fundChangeToolHandler.isSub(changeRequest);
//            //减币的账变在申请的时候就产生了
//            if (!isSub) {
//                glFundUserAccountBusiness.addBalance(changeRequest.getUserId(), changeRequest.getAmount(), DigitalCoinEnum.CNY);
//                if (!ObjectUtils.isEmpty(changeRequest.getValidWithdraw())) {
//                    ValidWithdrawalDto dto = new ValidWithdrawalDto(changeRequest.getUserId(),changeRequest.getValidWithdraw());
//                    fundProxyAccountBusiness.addValidWithdrawalSyncEs(dto);
//                }
//            }
//            /**
//             * 成功的业务逻辑
//             */
//            //减币 修改amount改为负值
//            if (changeRequest.getChangeType() == MsgEnum.SubCoin.value()) {
//                changeRequest.setAmount(new BigDecimal("-" + changeRequest.getAmount().toString()));
//            }
//
//            FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(), changeRequest.getChangeType(), changeRequest.getSubType());
//            int templeteId = ProjectConstant.SystemNoticeTempleteId.DEDUCTION_NO_REMARK;
//            //系统减币
//            if (changeOperateSubType.getOperateType() == FundConstant.ChangeOperateType.REDUCE) {
//                doSubcoin(changeRequest, user, status, remark, approverName, subType);
//            } else {
//                templeteId = ProjectConstant.SystemNoticeTempleteId.BONUS_NO_REMARK;
//                doAddCoin(changeRequest, user, remark, approverName, subType, changeOperateSubType);
//            }
//            //系统减币通知
//            NoticeSuccessDto successDto = new NoticeSuccessDto();
//            successDto.setAmount(changeRequest.getAmount());
//            successDto.setOrderId(changeRequest.getOrderId());
//            successDto.setUserId(user.getId());
//            successDto.setUserName(user.getUsername());
//            successDto.setRemark(changeRequest.getRemark());
//            successDto.setType(templeteId);
//            successDto.setSubTypeName(changeOperateSubType.getName());
//            noticeHandler.doSuccessNotice(successDto);
//
//            //上报
//
//        } catch (Exception e) {
//            log.error("doFundSecondApprove error", e);
//            throw new GlobalException(e.getMessage(), e);
//        }
//    }

    //资金调整代理活动保存记录并上报
    private void beforeInsert(GlFundChangeRequest request) throws GlobalException {
        GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(request.getUserId()));

        RPCResponse<GlActivityProxyDO> response = activityProxyService.findActivityById(request.getActId());
        if (RPCResponseUtils.isFail(response) || ObjectUtils.isEmpty(response.getData())) {
            throw new GlobalException("findById: 活动信息查询失败");
        }
        GlActivityProxyDO activityProxyDO = response.getData();
        GlFundChangeRequestActivityDO activityDO = GlFundChangeRequestActivityDO.builder()
                .actId(activityProxyDO.getId())
                .actName(activityProxyDO.getName())
                .applyer(glUser.getUsername())
                .createTime(new Date())
                .awardAmount(request.getAmount())
                .freezeAmount(request.getFreezeAmount())
                .rechargeAmount(request.getRechargeAmount())
                .status(ProjectConstant.Status.PENDING)
                .orderId(request.getOrderId())
                .updateTime(new Date())
                .build();

        RPCResponse<Boolean> saveResponse = fundChangeReqActivityService.save(activityDO);
        if (RPCResponseUtils.isFail(saveResponse) || !saveResponse.getData()) {
            throw new GlobalException("保存资金调整代理活动失败");
        }
        fundAdjustReport(request, glUser, ProjectConstant.Status.PENDING);
    }

    /**
     * 代理充送模板类红利上报成功
     *
     * @param changeRequest
     * @param user
     */
    public void fundAdjustReport(GlFundChangeRequest changeRequest, GlUserDO user, int status) throws GlobalException {

        RPCResponse<GlActivityProxyDO> response = activityProxyService.findActivityById(changeRequest.getActId());
        if (RPCResponseUtils.isFail(response) || ObjectUtils.isEmpty(response.getData())) {
            throw new GlobalException("findById: 活动信息查询失败");
        }
        GlActivityProxyDO activityProxyDO = response.getData();
        //活动红利上报
        Date now = new Date();
        BonusReport report = new BonusReport();
        report.setUuid(changeRequest.getOrderId());
        report.setUid(user.getId());
        report.setUserId(user.getId());
        report.setUserName(user.getUsername());
        report.setUserType(UserTypeEnum.valueOf(user.getUserType()));
        report.setParentId(user.getParentId());
        report.setParentName(user.getParentName());
        // todo 财务更新
        //report.setCoin(changeRequest.getCoinCode());
        report.setCoin("CNY");
        report.setAmount(changeRequest.getAmount().movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        report.setCreateTime(now);
        report.setRegTime(user.getRegisterDate());
        report.setStatus(status);
        report.setSubType(activityProxyDO.getName());
        report.setIsFake(user.getIsFake());
        report.setFinanceAdjustReason(changeRequest.getRemark());
        report.setRemark(activityProxyDO.getName());
        report.setActId(activityProxyDO.getId());
        reportService.bonusReport(report);
    }

//    /**
//     * 加币不计红利上报和计入红利是一样
//     *
//     * @param changeRequest
//     * @param user
//     * @param remark
//     * @param approverName
//     * @param subType
//     * @param changeOperateSubType
//     */
//    private void doAddCoin(GlFundChangeRequest changeRequest, GlUserDO user, String remark, String approverName, Integer subType, FundConstant.ChangeOperateSubType changeOperateSubType) throws GlobalException {
//
//        int type = changeRequest.getChangeType() == MsgEnum.Bonus.value() ? 11 : (changeRequest.getChangeType() == MsgEnum.AddCoin.value() ? 12 : 0);
//
//        //更新用户账户表 & 上报流水
//        this.updateUserAccount(changeRequest, user, new Date(), subType, type, approverName, remark);
//        BigDecimal balance = BigDecimal.ZERO;
//        //添加账变前后金额
//        balance = glFundUserAccountBusiness.getUserBalance(user.getId());
//        String reportRemark = "系统加币";
//        //加币不计入红利统计
//        if (changeOperateSubType.getOperateType() == FundConstant.ChangeOperateType.ADD_NOT_INCLUDE_PROFIT) {
//            glFundReportHandler.reportAddCoin(changeRequest, user, subType, balance, reportRemark);
//        }
//        //加币计入红利
//        else {
//            glFundReportHandler.reportBonus(changeRequest, user, subType, balance, reportRemark);
//        }
//    }

//    /**
//     * 减币
//     *
//     * @param changeRequest
//     * @param user
//     * @param status
//     * @param remark
//     * @param approverName
//     * @param subType
//     * @throws GlobalException
//     */
//    private void doSubcoin(GlFundChangeRequest changeRequest, GlUserDO user, Integer status, String remark, String approverName, Integer subType) throws GlobalException {
//        BigDecimal balance = null;
//
//        SubCoinReport report = new SubCoinReport();
//        report.setUuid(changeRequest.getOrderId());
//        report.setUid(user.getId());
//        report.setUserName(user.getUsername());
//        report.setUserType(UserTypeEnum.valueOf(user.getUserType()));
//        report.setParentId(user.getParentId());
//        report.setParentName(user.getParentName());
//        report.setAmount(changeRequest.getAmount().multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
//        report.setRegTime(user.getRegisterDate());
//        report.setFinanceAdjustReason(changeRequest.getFinanceAdjustReason());
//        //减币成功，记录前后账变信息
//        balance = glFundUserAccountBusiness.getUserBalance(user.getId());
//        //减币申请的时候已经上报，这里不再上报金额
//        // report.setBalanceAfter(balance.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
//        // report.setBalanceBefore(balance.add(changeRequest.getAmount().abs()).multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
//
//        //代理上报过后信用余额
//        if (user.getUserType() == UserConstant.Type.PROXY) {
//            FundProxyAccount proxyAccount = fundProxyAccountMapper.selectByPrimaryKey(user.getId());
//            BigDecimal creditAmount = proxyAccount.getCreditAmount();
//            BigDecimal creditAmountAfter = balance.compareTo(BigDecimal.ZERO) >= 0 ? creditAmount : creditAmount.subtract(balance.abs());
//            report.setCreditBalanceAfter(creditAmountAfter.multiply(BigDecimalUtils.TEN_THOUSAND).longValue());
//        }
//        report.setCreateTime(changeRequest.getCreateTime());
//        report.setTimestamp(changeRequest.getCreateTime());
//        report.setFinishTime(new Date());
//        StringBuffer sb = new StringBuffer();
//        sb.append(changeRequest.getAmount().compareTo(BigDecimal.ZERO) == 1 ? "系统加币" : "系统减币");
//        report.setRemark(sb.toString());
//        if (!ObjectUtils.isEmpty(subType)) {
//            report.setSubType(subType.toString());
//        }
//        report.setStatus(1);
//        report.setIsFake(user.getIsFake());
//        reportExtendHandler.extendReport(report);
//        log.info("reportSubCoin report = {}", JSON.toJSONString(report));
//        reportService.reportSubCoin(report);
//
//        List<Integer> subTypes = Lists.newArrayList(
//                FundConstant.ChangeOperateSubType.DEDUCT_WITH_SYSTEM.getValue(),
//                FundConstant.ChangeOperateSubType.DEDUCT_WITH_WRONG_POINT.getValue(),
//                FundConstant.ChangeOperateSubType.DEDUCT_WITH_PROXY_POINT.getValue()
//        );
//        //处理减币流水
//        if (user.getUserType().equals(UserConstant.Type.PLAYER) && (subTypes.stream().anyMatch(subType::equals))) {
//            //错误代充扣回系统自动生成一笔对应代理的加币
//            if (subType == FundConstant.ChangeOperateSubType.DEDUCT_WITH_PROXY_POINT.getValue()) {
//                Integer parentId = user.getParentId();
//                GlUserDO proxy = RPCResponseUtils.getData(glUserService.findById(parentId));
//                if (!ObjectUtils.isEmpty(proxy)) {
//                    FundProxyAccount proxyAccount = fundProxyAccountMapper.selectByPrimaryKey(parentId);
//                    if (!ObjectUtils.isEmpty(proxyAccount)) {
//                        //自动生成人工充值
//                        GlFundChangeRequest addRequest = new GlFundChangeRequest();
//                        addRequest.setAmount(changeRequest.getAmount().abs());
//                        addRequest.setFreezeAmount(BigDecimal.ZERO);
//                        addRequest.setChangeType(FundConstant.ChangeOperateType.ADD_NOT_INCLUDE_PROFIT);
//                        addRequest.setOrderId(redisService.getTradeNo(GameOrderPrefix.GAME_CZ.getCode()));
//                        StringBuffer autoRemark = new StringBuffer();
//                        autoRemark.append("加币").append(",").append(addRequest.getAmount().abs()).append(",").append("错误会员代充扣回");
//                        addRequest.setRemark(autoRemark.toString());
//                        addRequest.setStatus(3);
//                        addRequest.setUserId(parentId);
//                        addRequest.setUsername(proxy.getUsername());
//                        addRequest.setUserType(proxy.getUserType());
//                        addRequest.setSubType(FundConstant.ChangeOperateSubType.ADD_DEDUCT_WITH_WRONG_POINT.getValue());
//                        addRequest.setCreateTime(new Date());
//                        addRequest.setCreator("系统自动");
//                        glFundChangeRequestMapper.insert(addRequest);
//
//                        GlFundChangeApprove addApprove = new GlFundChangeApprove();
//                        addApprove.setCreateTime(new Date());
//                        addApprove.setCreator("系统自动");
//                        addApprove.setOrderId(addRequest.getOrderId());
//                        addApprove.setRemark(addRequest.getRemark());
//                        addApprove.setStatus(status);
//                        glFundChangeApproveMapper.insertSelective(addApprove);
//
//                        //更新余额
//                        glFundUserAccountBusiness.addBalance(addRequest.getUserId(), addRequest.getAmount(),DigitalCoinEnum.CNY);
//
//                        //上报加币记录
//                        BigDecimal proxyBalance = glFundUserAccountBusiness.getUserBalance(addRequest.getUserId());
//                        String reportRemark = "会员（" + user.getUsername() + "）代充扣回";
//                        changeRequest.setAmount(changeRequest.getAmount().abs());
//                        glFundReportHandler.reportAddCoin(addRequest, proxy, subType, proxyBalance, reportRemark);
//                        //系统加币通知
//                        NoticeSuccessDto successDto = new NoticeSuccessDto();
//                        successDto.setAmount(addRequest.getAmount());
//                        successDto.setOrderId(addRequest.getOrderId());
//                        successDto.setUserId(addRequest.getUserId());
//                        successDto.setUserName(addRequest.getUsername());
//                        successDto.setRemark(addRequest.getRemark());
//                        successDto.setType(ProjectConstant.SystemNoticeTempleteId.BONUS_NO_REMARK);
//                        FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(
//                                dynamicKey.getAppName(), addRequest.getChangeType(), addRequest.getSubType());
//                        successDto.setSubTypeName(changeOperateSubType.getName());
//                        noticeHandler.doSuccessNotice(successDto);
//
//                    }
//                }
//            }
//            //更新用户帐户余额表 & 上报流水
//            this.updateUserAccount(changeRequest, user, new Date(), subType, BettingBalanceEnum.SUBCOIN_FUND.getCode(), approverName, remark);
//        }
//    }

//    /**
//     * 修改用户流水需求
//     *
//     * @param changeRequest
//     * @param user
//     * @param now
//     * @param subType
//     * @param changeType
//     * @param approverName
//     * @param remark
//     */
//    private void updateUserAccount(GlFundChangeRequest changeRequest, GlUserDO user, Date now, Integer subType,
//                                   Integer changeType, String approverName, String remark) throws GlobalException {
//        if (UserConstant.Type.PLAYER != user.getUserType()) {
//            return;
//        }
//        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findById(changeRequest.getUserId());
//
//        FindBettingCommParamDO paramDO = new FindBettingCommParamDO();
//        paramDO.setUserId(user.getId());
//        paramDO.setStartTime(effectBet.getEffectStartTime().getTime());
//        paramDO.setEndTime(now.getTime());
//        paramDO.setGamePlatformIds(new ArrayList<>());
//        RPCResponse<BigDecimal> validBalance = bettingService.sumBettingEffectiveAmount(paramDO);
//        BigDecimal betEffect = RPCResponseUtils.getData(validBalance);
//        //调整前总流水需求
//        BigDecimal freezeBalanceBefore = effectBet.getRequiredBet();
//        //调整时剩余流水需求
//        BigDecimal leftFreezeBalance = freezeBalanceBefore.subtract(betEffect);
//        //调整后所需流水
//        BigDecimal freezeBalanceAfter = BigDecimal.ZERO;
//        //只有会员需要提供流水
//        GlWithdrawEffectBetDO betVO = new GlWithdrawEffectBetDO();
//        betVO.setUserId(changeRequest.getUserId());
//        betVO.setIsClean(false);
//        betVO.setEffectAmount(effectBet.getRequiredBet());
//        betVO.setChangeDate(effectBet.getEffectStartTime());
//        betVO.setAmount(changeRequest.getAmount());
//
//        if (null != changeRequest.getFreezeAmount()
//                && changeRequest.getFreezeAmount().compareTo(BigDecimal.ZERO) != 0) {
//            if (changeRequest.getChangeType() != FundConstant.ChangeOperateType.REDUCE) { //增加流水
//                if (leftFreezeBalance.compareTo(BigDecimal.ZERO) != 1) { //剩余流水 < 0 : 超出部分无效
//                    freezeBalanceAfter = changeRequest.getFreezeAmount();
//
//                    betVO.setEffectAmount(freezeBalanceAfter);
//                    betVO.setAmount(changeRequest.getAmount());
//                    betVO.setChangeDate(now);
//                    betVO.setIsClean(true);
//                } else {
//                    freezeBalanceAfter = freezeBalanceBefore.add(changeRequest.getFreezeAmount());
//
//                    betVO.setAmount(changeRequest.getAmount());
//                    betVO.setEffectAmount(freezeBalanceAfter);
//                    betVO.setChangeDate(effectBet.getEffectStartTime());
//                }
//            } else {
//                if (freezeBalanceBefore.subtract(changeRequest.getFreezeAmount()).compareTo(BigDecimal.ZERO) < 0) {
//                    freezeBalanceAfter = BigDecimal.ZERO;
//
//                    betVO.setIsClean(true);
//                    betVO.setChangeDate(now);
//                    betVO.setAmount(BigDecimal.ZERO);
//                    betVO.setEffectAmount(BigDecimal.ZERO);
//                } else {
//                    freezeBalanceAfter = freezeBalanceBefore.subtract(changeRequest.getFreezeAmount());
//
//                    betVO.setChangeDate(effectBet.getEffectStartTime());
//                    betVO.setAmount(changeRequest.getAmount());
//                    betVO.setEffectAmount(freezeBalanceAfter);
//                }
//            }
//
//            BettingBalanceReport bettingBalanceReport = new BettingBalanceReport();
//            bettingBalanceReport.setUid(changeRequest.getUserId());
//            bettingBalanceReport.setOrderId(changeRequest.getOrderId());
//            bettingBalanceReport.setType(changeType);//调整类型
//            bettingBalanceReport.setAmount(changeRequest.getAmount().multiply(new BigDecimal(10000)));
//            bettingBalanceReport.setMagnificationFactor(BigDecimal.ONE);
//            //当前单笔流水需求
//            bettingBalanceReport.setSingleBettingDesire(changeRequest.getFreezeAmount().multiply(new BigDecimal(10000)));//定额流水需求 * 10000
//            //当前流水总需求
//            bettingBalanceReport.setTotalBettingDesire(freezeBalanceAfter.multiply(new BigDecimal(10000)));
//            //已完成流水
//            bettingBalanceReport.setBetEffect(betEffect.multiply(new BigDecimal(10000)));
//            //之前的剩余流水需求
//            bettingBalanceReport.setLeftBettingDesireBefore(freezeBalanceBefore.multiply(new BigDecimal(10000)));
//            //之后的剩余流水需求
//            bettingBalanceReport.setLeftBettingDesireAfter(freezeBalanceAfter.multiply(new BigDecimal(10000)));
//            //真实/虚拟账户
//            bettingBalanceReport.setOperator(approverName);
//            bettingBalanceReport.setIsFake(user.getIsFake());
//            bettingBalanceReport.setRemark(remark);
//            bettingBalanceReport.setFinishTime(now);
//            //添加资金流水子类型
//            bettingBalanceReport.setSubType(subType);
//            reportService.reportBettingBalance(bettingBalanceReport);
//        }
//        log.info("updateUserAccount_GlWithdrawEffectBetVO:{}", JSON.toJSONString(betVO));
//        glWithdrawEffectBetBusiness.syncWithdrawEffect(betVO);
//    }


    /***
     * 给指定用户（虚拟用户）做资金调整，并不用审核
     * @param user
     * @param balance
     * @param creator
     * @param remark
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustUserBalance(GlUserDO user, Integer changeType, BigDecimal balance, String creator, Integer value, String remark) throws GlobalException {
        FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(), changeType, value);
        if (changeOperateSubType == null) {
            throw new GlobalException("操作类型错误");
        }
        GlFundChangeRequest request = new GlFundChangeRequest();
        Date now = new Date();
        request.setAmount(balance);
        request.setFreezeAmount(BigDecimal.ZERO);
        int newChangeType = changeType == FundConstant.ChangeOperateType.REDUCE
                ? FundConstant.ChangeOperateType.REDUCE : FundConstant.ChangeOperateType.ADD_NOT_INCLUDE_PROFIT;
        request.setChangeType(newChangeType);//1018|加币-不计红利
        request.setCreateTime(now);
        request.setCreator(creator);
        request.setRemark(remark + "," + balance);
        request.setOrderId(redisService.getTradeNo(GameOrderPrefix.GAME_CZ.getCode()));
        request.setStatus(FundConstant.ChangeReqStatus.SECOND_APPROVAL_ALLOW); //二审通过
        request.setUserId(user.getId());
        request.setUsername(user.getUsername());
        request.setUserType(user.getUserType());
        request.setSubType(changeOperateSubType.getValue());// 虚拟额度
        request.setFirstApprover(creator);
        request.setFirstRemark(remark);
        request.setFirstTime(now);
        request.setSecondApprover(creator);
        request.setSecondRemark(remark);
        request.setSecondTime(now);
        glFundChangeRequestMapper.insert(request);

        GlFundChangeApprove approve = new GlFundChangeApprove();
        approve.setCreateTime(now);
        approve.setCreator(creator);
        approve.setOrderId(request.getOrderId());
        approve.setRemark(remark);
        approve.setStatus(FundConstant.ChangeReqStatus.SECOND_APPROVAL_ALLOW);
        glFundChangeApproveMapper.insertSelective(approve);
        //财务用户加币
        glFundUserAccountBusiness.addBalance(request.getUserId(), request.getAmount(), DigitalCoinEnum.CNY);
        //系统减币通知
        boolean isReduce = changeOperateSubType.getOperateType() == FundConstant.ChangeOperateType.REDUCE;
        int templateId = isReduce ? ProjectConstant.SystemNoticeTempleteId.DEDUCTION_NO_REMARK :
                ProjectConstant.SystemNoticeTempleteId.BONUS_NO_REMARK;
        String text = isReduce ? "系统减币" : "系统加币";
        //更新用户账户表 & 上报流水
        if (isReduce) {
            glFundReportHandler.reportSubCoin(request, user, 1);
        } else {
            glFundReportHandler.reportAddCoin(request, user, request.getSubType(), balance, text);
        }

        NoticeSuccessDto successDto = new NoticeSuccessDto();
        successDto.setAmount(request.getAmount());
        successDto.setOrderId(request.getOrderId());
        successDto.setUserId(request.getUserId());
        successDto.setUserName(request.getUsername());
        successDto.setRemark(request.getRemark());
        successDto.setType(templateId);
        successDto.setSubTypeName(changeOperateSubType.getName());
        noticeHandler.doSuccessNotice(successDto);
    }


    public NameTypeEnum getNameType(String name) {
        if (StringUtils.isEmpty(name)) {
            return NameTypeEnum.ALL;
        }
        if (RegexValidator.isChinese(name)) {
            return NameTypeEnum.CHINESE;
        }else if (RegexValidator.isEnglish(name)) {
            return NameTypeEnum.ENGLISH;
        }else if (RegexValidator.isMinority(name)) {
            return NameTypeEnum.MINORITY;
        } else {
            log.debug("匹配姓名类型失败 :" + name);
        }
        return NameTypeEnum.ALL;
    }
}
