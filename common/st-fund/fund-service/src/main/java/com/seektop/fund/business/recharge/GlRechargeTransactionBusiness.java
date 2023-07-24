package com.seektop.fund.business.recharge;

import com.alibaba.fastjson.JSON;
import com.seektop.agent.service.CommCommissionService;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.DateConstant;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.service.RechargeService;
import com.seektop.digital.mapper.DigitalReceiveWalletMapper;
import com.seektop.digital.model.DigitalReceiveWallet;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.PlatformEnum;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.digital.DigitalReceiveWalletStatusEnum;
import com.seektop.enumerate.fund.BankEnum;
import com.seektop.enumerate.fund.RechargePaymentEnum;
import com.seektop.enumerate.fund.RechargeStatusEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.param.recharge.RechargeApproveDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeRequestDO;
import com.seektop.fund.controller.backend.param.recharge.RequestRechargeRejectDO;
import com.seektop.fund.customer.BankActivityProducer;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.fund.handler.ReportExtendHandler;
import com.seektop.fund.mapper.*;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.RechargeNotify;
import com.seektop.report.fund.RechargeReport;
import com.seektop.system.service.SystemNoticeTemplateService;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class GlRechargeTransactionBusiness {

    @Autowired
    private GlRechargeMapper glRechargeMapper;

    @Autowired
    private GlRechargePayMapper glRechargePayMapper;

    @Autowired
    private GlPaymentMerchantaccountMapper glPaymentMerchantaccountMapper;

    @Autowired
    private GlPaymentMerchantAppMapper glPaymentMerchantAppMapper;

    @Autowired
    private GlPaymentUserCardMapper glPaymentUserCardMapper;

    @Autowired
    private BankActivityProducer bankActivityProducer;

    @Resource(name = "rechargeNoticeHandler")
    private NoticeHandler noticeHandler;

    @DubboReference(timeout = 5000, retries = 3)
    private RechargeService rechargeService;

    @DubboReference(timeout = 3000, retries = 3)
    private SystemNoticeTemplateService systemNoticeTemplateService;

    @DubboReference(retries = 2, timeout = 3000)
    private CommCommissionService commCommissionService;

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Resource
    private RedisService redisService;
    @Resource
    private UserVipUtils userVipUtils;

    @Resource
    private GlRechargeSuccessApproveMapper glRechargeSuccessApproveMapper;

    @Resource
    private GlRechargeSuccessRequestMapper glRechargeSuccessRequestMapper;

    @Resource
    private DigitalReceiveWalletMapper digitalReceiveWalletMapper;

    @Resource
    private ReportService reportService;

    @Resource
    private ReportExtendHandler reportExtendHandler;

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Resource
    private GlPaymentMerchantFeeBusiness glPaymentMerchantFeeBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private GlRechargeRelationBusiness glRechargeRelationBusiness;

    @Resource
    private DynamicKey dynamicKey;

    @Resource
    private GlRechargeReceiveInfoMapper glRechargeReceiveInfoMapper;

    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;


    /**
     * 保存充值&上报
     *
     * @param recharge
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doRechargeCreate(GlRecharge recharge, GlPaymentMerchantApp glPaymentMerchantApp, GlUserDO userDO, GlRechargeReceiveInfo receiveInfo, ParamBaseDO paramBaseDO, GlRechargeResult result)
            throws GlobalException {

        // 增加商户订单总数
        addMerchantAccountCount(recharge);

        //创建订单失败-保存充值异常记录
        if (result.getErrorCode() != FundConstant.RechargeErrorCode.NORMAL) {
            glRechargeErrorBusiness.save(userDO, recharge.getClientType(), recharge.getCreateDate(), glPaymentMerchantApp, result);
            return;
        }
        //保存充值订单记录
        glRechargeMapper.insertSelective(recharge);

        //极速转卡过期时间TTL
        if (recharge.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
            setTTL(recharge);
        }

        if (null != receiveInfo) {
            //保存充值收款账户信息
            glRechargeReceiveInfoMapper.insertSelective(receiveInfo);
        }

        //充值记录上报
        RechargeReport rechargeReport = new RechargeReport();
        rechargeReport.setUuid(recharge.getOrderId());
        rechargeReport.setUid(recharge.getUserId());
        rechargeReport.setUserId(recharge.getUserId());
        rechargeReport.setPlatform(PlatformEnum.valueOf(recharge.getClientType()));
        rechargeReport.setDeviceId(paramBaseDO.getHeaderDeviceId());
        rechargeReport.setDomain(paramBaseDO.getRequestUrl());
        rechargeReport.setIp(paramBaseDO.getRequestIp());
        rechargeReport.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setStatus(RechargeStatusEnum.UNPAID);
        rechargeReport.setUserName(recharge.getUsername());
        rechargeReport.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
        rechargeReport.setParentName(userDO.getParentName());
        rechargeReport.setParentId(userDO.getParentId());
        rechargeReport.setBank(BankEnum.valueOf(recharge.getBankId()));
        rechargeReport.setMerchant(recharge.getMerchantId());
        rechargeReport.setPayment(RechargePaymentEnum.valueOf(recharge.getPaymentId()));
        rechargeReport.setSubType(glPaymentMerchantApp.getPaymentName());
        rechargeReport.setCreateTime(recharge.getCreateDate());
        rechargeReport.setTimestamp(recharge.getCreateDate());
        rechargeReport.setRegTime(userDO.getRegisterDate());
        rechargeReport.setMerchantCode(glPaymentMerchantApp.getMerchantCode());
        rechargeReport.setChannelId(glPaymentMerchantApp.getChannelId());
        rechargeReport.setChannelName(glPaymentMerchantApp.getChannelName());
        rechargeReport.setIsFake(userDO.getIsFake());
        rechargeReport.setKeyword(recharge.getKeyword());
        rechargeReport.setCoin(recharge.getCoin());
        // 获取账户余额设置账变前后金额
        BigDecimal balance = glFundUserAccountBusiness.getUserBalance(recharge.getUserId()).multiply(BigDecimal.valueOf(100000000));
        if (ObjectUtils.isEmpty(balance)) {
            balance = new BigDecimal(0);
        }
        rechargeReport.setBalanceBefore(balance.longValue());
        rechargeReport.setBalanceAfter(balance.longValue());
        //VIP等级
        if (userDO.getUserType().equals(UserConstant.UserType.PLAYER)) {
            UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
            rechargeReport.setVipLevel(null == vipCache ? 0 : vipCache.getVipLevel());
        } else {
            rechargeReport.setVipLevel(-1);
        }

        //用户层级
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        rechargeReport.setUserLevel(userlevel.getLevelId());
        rechargeReport.setUserLevelName(userlevel.getName());

        if (null != receiveInfo && recharge.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {

            BigDecimal usdtAmount = receiveInfo.getDigitalAmount();
            if (null != usdtAmount) {
                usdtAmount = usdtAmount.multiply(BigDecimal.valueOf(100000000));
            } else {
                usdtAmount = BigDecimal.ZERO;
            }

            BigDecimal rate = receiveInfo.getRate();
            if (null != rate) {
                rate = rate.multiply(BigDecimal.valueOf(100000000));
            } else {
                rate = BigDecimal.ZERO;
            }
            rechargeReport.setUsdtAmount(usdtAmount.longValue());
            rechargeReport.setRate(rate.longValue());
        }
        reportService.rechargeReport(rechargeReport);
    }

    private void setTTL(GlRecharge recharge) {
        try {
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            String key = String.format(KeyConstant.C2C.C2C_RECHARGE_TTL,recharge.getOrderId());
            redisService.set(key,"ttl",configDO.getRechargePaymentTimeout() * 60);
        } catch (Exception e) {
            log.error("极速转卡订单设置过期ttl异常", e);
        }
    }

    /**
     * 后台创建订单
     *
     * @param recharge
     * @param glPaymentMerchantApp
     * @param userDO
     * @param paramBaseDO
     * @param relation
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doRechargeForBackend(GlRecharge recharge, GlPaymentMerchantApp glPaymentMerchantApp, GlUserDO userDO,
                                     GlRechargeReceiveInfo receiveInfo, ParamBaseDO paramBaseDO, GlRechargeRelation relation, GlRechargeResult result) throws GlobalException {
        this.doRechargeCreate(recharge, glPaymentMerchantApp, userDO, receiveInfo, paramBaseDO, result);
        glRechargeRelationBusiness.save(relation);
    }

    /**
     * 充值渠道加入用户充值失败队列
     *
     * @param recharge
     */
    private void addRechargeFail(GlRecharge recharge) {
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppMapper.selectOneByEntity(recharge.getPaymentId(), recharge.getMerchantId(), null);
        if (null != merchantApp) {
            List<Integer> failList = new ArrayList<>();
            RedisResult<Integer> resultList = redisService.getListResult(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST +
                    recharge.getUserId(), Integer.class);
            if (resultList != null && !ObjectUtils.isEmpty(resultList.getListResult())) {
                failList = resultList.getListResult();
            }
            failList.remove(merchantApp.getId());
            failList.add(0, merchantApp.getId());
            if (failList.size() > 10) {
                failList = failList.subList(0, 10);
            }
            redisService.set(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + recharge.getUserId(), failList, DateConstant.SECOND.DAY);
        }
    }

    /**
     * 取消充值
     *
     * @param orderId
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doRechargeCancel(String orderId) throws GlobalException {
        //1、更新充值订单状态
        GlRecharge recharge = glRechargeMapper.selectForUpdate(orderId);
        if (recharge.getStatus() != FundConstant.RechargeStatus.PENDING) {
            throw new GlobalException(ResultCode.DATA_ERROR, "订单状态异常请联系客服.");
        }
        Date now = new Date();

        recharge.setStatus(FundConstant.RechargeStatus.FAILED);
        recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHAEGE_CANCEL);
        recharge.setLastUpdate(now);
        glRechargeMapper.updateByPrimaryKeySelective(recharge);

        this.addRechargeFail(recharge);

        // 设置收币钱包可用
        this.setReceiveWalletEnable(orderId);

        //2.充值订单上报
        RechargeReport report = new RechargeReport();
        report.setUuid(recharge.getOrderId());
        report.setUid(recharge.getUserId());
        report.setStatus(RechargeStatusEnum.valueOf(recharge.getStatus()));
        report.setFinishTime(now);
        report.setTimestamp(recharge.getCreateDate());
        report.setCreateTime(recharge.getCreateDate());
        reportExtendHandler.extendReport(report);
        reportService.rechargeReport(report);

        //发送充值失败通知
        doRechargePayFailNotice(recharge);
    }

    /**
     * 充值订单自动失效
     *
     * @param rechargeDO
     */
    @Transactional(rollbackFor = Exception.class)
    public void doRechargeTimeOut(GlRechargeDO rechargeDO) throws GlobalException {
        Date now = new Date();

        // 修改充值订单状态
        GlRecharge recharge = glRechargeMapper.selectForUpdate(rechargeDO.getOrderId());
        if (recharge == null || recharge.getStatus() != FundConstant.RechargeStatus.PENDING) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        recharge.setStatus(FundConstant.RechargeStatus.FAILED);
        recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHARGE_TIMEOUT);
        recharge.setLastUpdate(now);
        glRechargeMapper.updateByPrimaryKeySelective(recharge);

        this.addRechargeFail(recharge);

        RechargeReport report = new RechargeReport();
        report.setUuid(recharge.getOrderId());
        report.setUid(recharge.getUserId());
        report.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        //失败的账变，账变前后金额皆为当前账户余额；添加账变时间
        BigDecimal balance = glFundUserAccountBusiness.getUserBalance(recharge.getUserId()).multiply(BigDecimal.valueOf(100000000));
        if (balance == null) {
            balance = new BigDecimal(0);
        }
        report.setBalanceBefore(balance.longValue());
        report.setBalanceAfter(balance.longValue());
        report.setFinishTime(now);
        report.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setStatus(RechargeStatusEnum.FAIL);
        report.setCreateTime(recharge.getCreateDate());
        report.setTimestamp(recharge.getCreateDate());
        reportExtendHandler.extendReport(report);
        reportService.rechargeReport(report);
    }

    /**
     * 充值订单-人工拒绝补单
     *
     * @param admin
     * @param rejectDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void rejectRequest(GlAdminDO admin, RequestRechargeRejectDO rejectDO) throws GlobalException {
        Date now = new Date();

        // 插入补单申请记录
        GlRechargeSuccessRequest req = glRechargeSuccessRequestMapper.selectByPrimaryKey(rejectDO.getOrderId());
        if (null != req) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        req = new GlRechargeSuccessRequest();
        req.setOrderId(rejectDO.getOrderId());
        req.setUserId(admin.getUserId());
        req.setUsername(admin.getUsername());
        req.setAmount(BigDecimal.ZERO);
        req.setRemark(rejectDO.getRemark());
        req.setCreateDate(now);
        req.setLastUpdate(now);
        req.setStatus(FundConstant.RechargeApprove.APPROVAL_REJECT);
        glRechargeSuccessRequestMapper.insert(req);

        //插入补单审核记录
        GlRechargeSuccessApprove approve = glRechargeSuccessApproveMapper.selectByPrimaryKey(rejectDO.getOrderId());
        if (null != approve) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        approve = new GlRechargeSuccessApprove();
        approve.setOrderId(rejectDO.getOrderId());
        approve.setUserId(admin.getUserId());
        approve.setUsername(admin.getUsername());
        approve.setAmount(BigDecimal.ZERO);
        approve.setRemark(rejectDO.getRemark());
        approve.setStatus(FundConstant.RechargeApprove.APPROVAL_REJECT);
        approve.setCreateDate(now);
        glRechargeSuccessApproveMapper.insert(approve);

        //更改充值订单状态
        GlRecharge recharge = glRechargeMapper.selectForUpdate(rejectDO.getOrderId());
        if (null == recharge) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        recharge.setStatus(ProjectConstant.RechargeStatus.FAILED);
        recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHAEGE_MANUAL_AUDIT_REJECTED);
        recharge.setRemark(rejectDO.getRemark());
        recharge.setLastUpdate(now);
        glRechargeMapper.updateByPrimaryKey(recharge);

        /**
         * 上报充值记录状态->失败
         */
        RechargeReport rechargeReport = new RechargeReport();
        rechargeReport.setUuid(recharge.getOrderId());
        rechargeReport.setUid(recharge.getUserId());
        rechargeReport.setTimestamp(recharge.getCreateDate());
        rechargeReport.setCreateTime(recharge.getCreateDate());
        rechargeReport.setStatus(RechargeStatusEnum.FAIL);
        //失败时，账变前后金额一致，皆为当前金额；账变时间为当前时间
        BigDecimal userBalance = glFundUserAccountBusiness.getUserBalance(recharge.getUserId());
        rechargeReport.setBalanceBefore(userBalance.multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setBalanceAfter(userBalance.multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setFinishTime(now);
        reportExtendHandler.extendReport(rechargeReport);
        reportService.rechargeReport(rechargeReport);

        //发送充值失败通知
        doRechargePayFailNotice(recharge);

        //极速充值订单通知撮合系统
        if (!ObjectUtils.isEmpty(recharge.getChannelId())
                && recharge.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
            glRechargeBusiness.doRechargeCancel(recharge);
        }
    }

    /**
     * 充值订单-申请补单审核
     *
     * @param adminDO
     * @param requestDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void requestRecharge(GlAdminDO adminDO, RechargeRequestDO requestDO) throws GlobalException {
        // 申请已存在
        GlRechargeSuccessRequest request = glRechargeSuccessRequestMapper.selectByPrimaryKey(requestDO.getOrderId());
        if (null != request) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        GlRecharge recharge = glRechargeMapper.selectForUpdate(requestDO.getOrderId());
        //充值记录不存在、充值记录已完成、充值记录待审核
        if (null == recharge || recharge.getStatus() == FundConstant.RechargeStatus.SUCCESS
                || recharge.getStatus() == FundConstant.RechargeStatus.REVIEW) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        Date now = new Date();

        String imgPath = "";//图片地址
        if (requestDO.getAttachments() != null && requestDO.getAttachments().size() > 0) {
            imgPath = imgPath + requestDO.getAttachments().get(0);
            for (int i = 1; i < requestDO.getAttachments().size(); i++) {
                imgPath = imgPath + "|" + requestDO.getAttachments().get(i);
            }
        }

        //插入补单申请记录
        request = new GlRechargeSuccessRequest();
        request.setOrderId(recharge.getOrderId());
        request.setUserId(adminDO.getUserId());
        request.setUsername(adminDO.getUsername());
        request.setAmount(requestDO.getAmount());
        request.setRemark(requestDO.getRemark());
        request.setStatus(FundConstant.ChangeReqStatus.PENDING_APPROVAL);
        request.setReqImg(imgPath);
        request.setCreateDate(now);
        request.setLastUpdate(now);
        glRechargeSuccessRequestMapper.insert(request);

        //更新充值订单
        if (StringUtils.isNotEmpty(requestDO.getPayeeName()) && requestDO.getPayeeBankId() > 0) {
            //更新订单收款账户信息
            recharge.setCardUsername(requestDO.getPayeeName());
            recharge.setBankId(requestDO.getPayeeBankId());
            recharge.setBankName(FundConstant.bankNameMap.get(requestDO.getPayeeBankId()));
        }
        recharge.setStatus(FundConstant.RechargeStatus.REVIEW);
        recharge.setSubStatus(null);
        recharge.setLastUpdate(now);
        glRechargeMapper.updateForRequest(recharge);
    }


    /**
     * 充值补单审核
     *
     * @param admin
     * @param approveDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doRechargeApprove(GlAdminDO admin, RechargeApproveDO approveDO) throws GlobalException {
        log.info("approveDO:{}", JSON.toJSONString(approveDO));
        Date now = new Date();
        //1.更新充值补单申请记录
        GlRechargeSuccessRequest request = glRechargeSuccessRequestMapper.selectByPrimaryKey(approveDO.getOrderId());
        if (request == null || request.getStatus() != FundConstant.RechargeApprove.PENDING) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        request.setStatus(approveDO.getStatus());
        request.setLastUpdate(now);
        glRechargeSuccessRequestMapper.updateByPrimaryKeySelective(request);

        //2.更新充值补单审核记录
        GlRechargeSuccessApprove approve = glRechargeSuccessApproveMapper.selectByPrimaryKey(approveDO.getOrderId());
        if (approve != null) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        approve = new GlRechargeSuccessApprove();
        approve.setOrderId(approveDO.getOrderId());
        approve.setUserId(admin.getUserId());
        approve.setUsername(admin.getUsername());
        approve.setAmount(request.getAmount());
        approve.setStatus(approveDO.getStatus());
        approve.setCreateDate(now);
        approve.setRemark(approveDO.getRemark());
        glRechargeSuccessApproveMapper.insert(approve);

        // 3.修改充值订单记录
        GlRecharge recharge = glRechargeMapper.selectForUpdate(approveDO.getOrderId());
        if (recharge == null || recharge.getStatus() != FundConstant.RechargeStatus.REVIEW) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        //审核通过
        if (approveDO.getStatus() == FundConstant.RechargeApprove.APPROVAL_ALLOW) {
            recharge.setStatus(FundConstant.RechargeStatus.SUCCESS);
            recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHAEGE_AUDIT_SUCCESS);
            recharge.setLastUpdate(now);
            // 补单金额不等于订单金额
            if (request.getAmount().compareTo(recharge.getAmount()) != 0 && recharge.getCoin().equals(DigitalCoinEnum.CNY.getCode())) {
                //重新计算手续费
                GlPaymentMerchantFee merchantFee = glPaymentMerchantFeeBusiness.findFee(recharge.getLimitType(), recharge.getMerchantId(), recharge.getPaymentId());
                if (null == merchantFee || merchantFee.getFeeRate().compareTo(BigDecimal.ZERO) == -1) {
                    throw new GlobalException(ResultCode.DATA_ERROR, "商户手续费配置异常,请检查.");
                }
                BigDecimal fee = request.getAmount().multiply(merchantFee.getFeeRate()).divide(BigDecimal.valueOf(100));
                if (fee.compareTo(merchantFee.getMaxFee()) == 1) {
                    fee = merchantFee.getMaxFee();
                }
                recharge.setFee(fee);
            }
            //填充GlRechargePay
            GlRechargePay pay = new GlRechargePay();
            pay.setOrderId(approveDO.getOrderId());
            pay.setAmount(request.getAmount());
            pay.setFee(BigDecimal.ZERO);
            pay.setPayDate(now);
            pay.setCreateDate(now);

            this.doPaymentSuccess(recharge, pay);
            //增加商户充值金额
            this.addMerchantAccountSuccess(recharge, request.getAmount(), true);
        } else {
            //更新充值订单记录
            recharge.setStatus(FundConstant.RechargeStatus.FAILED);
            recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHAEGE_AUDIT_REJECTED);
            recharge.setLastUpdate(now);
            doRechargeFailed(recharge);
            //充值失败推送通知
            this.doRechargePayFailNotice(recharge);
        }
    }

    /**
     * 更新USDT充值订单实际汇率
     * @param recharge
     * @param isNotify 是否为回调
     * @param notify 实际支付USDT数量
     */
    private void updateRechargeApproveReceiveInfo(GlRecharge recharge, RechargeNotify notify, boolean isNotify) {
        if (recharge.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY || recharge.getPaymentId() == FundConstant.PaymentType.RMB_PAY) {
            GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(recharge.getOrderId());
            if (isNotify && notify.getPayDigitalAmount() != null && BigDecimalUtils.moreThanZero(notify.getPayDigitalAmount())) {
                if (org.apache.commons.lang3.ObjectUtils.isNotEmpty(notify.getRealRate()) && BigDecimalUtils.moreThanZero(notify.getRealRate())) {
                    receiveInfo.setRealRate(notify.getRealRate());
                } else {
                    receiveInfo.setRealRate(notify.getAmount().divide(notify.getPayDigitalAmount() , 2, RoundingMode.DOWN));
                }
                //实际支付USDT数量
                receiveInfo.setKeyword(notify.getPayDigitalAmount().toString());
            }
            glRechargeReceiveInfoMapper.updateByPrimaryKeySelective(receiveInfo);
        }
    }

    /**
     * 支付成功
     *
     * @param notify
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doNotifySuccess(RechargeNotify notify) throws GlobalException {
        Date now = new Date();
        GlRecharge recharge = glRechargeMapper.selectByPrimaryKey(notify.getOrderId());
        if (recharge == null || recharge.getStatus().equals(ProjectConstant.RechargeStatus.SUCCESS)) {
            return;
        }

        recharge.setStatus(FundConstant.RechargeStatus.SUCCESS);
        recharge.setSubStatus(FundConstant.RechargeSubStatus.RECHARGE_SUCCESSED);
        //针对ST补单回调，调整实际收款账户信息
        if (null != notify.getBankId()) {
            recharge.setBankId(notify.getBankId());
            recharge.setBankName(notify.getBankName());
            recharge.setCardUsername(notify.getBankCardName());
            recharge.setCardNo(notify.getBankCardNo());
        }
        recharge.setLastUpdate(now);
        if (recharge.getChannelId() == FundConstant.PaymentChannel.STPAYER
                && recharge.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY) {
            recharge.setKeyword(notify.getPayAddress());
        }

        GlRechargePay rechargePay = new GlRechargePay();
        rechargePay.setOrderId(notify.getOrderId());
        rechargePay.setAmount(notify.getAmount());
        rechargePay.setFee(BigDecimal.ZERO);
        rechargePay.setThirdOrderId(notify.getThirdOrderId());
        rechargePay.setPayDate(now);
        rechargePay.setCreateDate(now);

        //检查是否有补单审核申请 -> 审核通过
        GlRechargeSuccessRequest rechargeRequest = glRechargeSuccessRequestMapper.selectByPrimaryKey(notify.getOrderId());
        if (null != rechargeRequest) {
            rechargeRequest.setStatus(FundConstant.RechargeApprove.APPROVAL_ALLOW);
            rechargeRequest.setLastUpdate(now);
            rechargeRequest.setAmount(notify.getAmount());
            glRechargeSuccessRequestMapper.updateByPrimaryKey(rechargeRequest);

            GlRechargeSuccessApprove approve = glRechargeSuccessApproveMapper.selectByPrimaryKey(notify.getOrderId());
            if (null == approve) {
                GlRechargeSuccessApprove insertApprove = new GlRechargeSuccessApprove();
                insertApprove.setOrderId(notify.getOrderId());
                insertApprove.setUserId(1);
                insertApprove.setUsername("admin");
                insertApprove.setAmount(notify.getAmount());
                insertApprove.setStatus(FundConstant.RechargeApprove.APPROVAL_ALLOW);
                insertApprove.setCreateDate(now);
                glRechargeSuccessApproveMapper.insert(insertApprove);
            } else {
                approve.setStatus(FundConstant.RechargeApprove.APPROVAL_ALLOW);
                approve.setUserId(1);
                approve.setUsername("admin");
                approve.setAmount(notify.getAmount());
                glRechargeSuccessApproveMapper.updateByPrimaryKey(approve);
            }
        }
        this.doPaymentSuccess(recharge, rechargePay);

        this.addMerchantAccountSuccess(recharge, rechargePay.getAmount(), false);

        //更新USDT订单汇率
        this.updateRechargeApproveReceiveInfo(recharge, notify, true);
    }

    /**
     * 补单审核成功、支付成功
     *
     * @param recharge
     * @param pay
     */
    public void doPaymentSuccess(GlRecharge recharge, GlRechargePay pay) throws GlobalException {
        //首存校验
        Integer isFirst = 0;
//        RPCResponse<KV3Result<String, BigDecimal, Date, Integer>> kv3ResultRPCResponse = rechargeService.firstRechargeInfo(recharge.getUserId());
//        if (RPCResponseUtils.isSuccess(kv3ResultRPCResponse)) {
//            KV3Result<String, BigDecimal, Date, Integer> data = kv3ResultRPCResponse.getData();
//            log.info("获取用户首存信息 data:{}", JSON.toJSONString(data));
//            if (null == data) {
//                isFirst = 1;
//            }
//        } else {
//            log.error("获取用户首存信息查询异常,充值订单号：{}", recharge.getOrderId());
//        }
        if (glRechargeMapper.isFirst(recharge.getUserId())) {
            isFirst = 1;
        }
        //1.更新充值订单
        glRechargeMapper.updateByPrimaryKeySelective(recharge);

        //2.插入充值支付信息
        glRechargePayMapper.insertSelective(pay);
        //3.更新用户中心钱包、发送充值到账通知;返回帐变之后的余额
        BigDecimal balance = glFundUserAccountBusiness.doRechargeSuccess(recharge, pay.getAmount());
        //4.上报充值记录
        this.doRechargeSuccesssReport(recharge, pay, balance, isFirst);

        String key = RedisKeyHelper.USER_CANCEL_RECHARGE_COUNT + recharge.getUserId();
        redisService.delete(key);

    }


    /**
     * 补单审核拒绝
     *
     * @param recharge
     */
    public void doRechargeFailed(GlRecharge recharge) {
        // 修改充值订单状态
        glRechargeMapper.updateByPrimaryKeySelective(recharge);

        RechargeReport report = new RechargeReport();
        report.setUuid(recharge.getOrderId());
        report.setUid(recharge.getUserId());
        report.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        //失败的账变，账变前后金额皆为当前账户余额；添加账变时间
        BigDecimal balance = glFundUserAccountBusiness.getUserAccountBalance(recharge.getUserId()).multiply(BigDecimal.valueOf(100000000));
        report.setBalanceBefore(balance.longValue());
        report.setBalanceAfter(balance.longValue());
        report.setFinishTime(recharge.getLastUpdate());
        report.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setStatus(RechargeStatusEnum.FAIL);
        report.setCreateTime(recharge.getCreateDate());
        report.setTimestamp(recharge.getCreateDate());
        reportExtendHandler.extendReport(report);
        reportService.rechargeReport(report);
    }

    /**
     * 充值失败推送通知
     *
     * @param recharge
     */
    private void doRechargePayFailNotice(GlRecharge recharge) {
        NoticeFailDto failDto = new NoticeFailDto();
        failDto.setAmount(recharge.getAmount());
        failDto.setUserId(recharge.getUserId());
        failDto.setUserName(recharge.getUsername());
        failDto.setOrderId(recharge.getOrderId());
        failDto.setCoin(DigitalCoinEnum.getDigitalCoin(recharge.getCoin()).getDesc());
        noticeHandler.doFailNotice(failDto);
    }

    /**
     * 充值成功，记录上报
     *
     * @param recharge
     */
    private void doRechargeSuccesssReport(GlRecharge recharge, GlRechargePay rechargePay, BigDecimal balance, Integer isFirst) throws GlobalException {

        GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(recharge.getUserId()));

        RechargeReport report = new RechargeReport();
        report.setUuid(recharge.getOrderId());
        report.setUid(recharge.getUserId());
        report.setUserId(recharge.getUserId());
        report.setParentName(userDO.getParentName());
        report.setParentId(userDO.getParentId());
        report.setPayAmount(rechargePay.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setBalanceBefore(balance.subtract(rechargePay.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setBalanceAfter(balance.multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setFinishTime(rechargePay.getPayDate());
        report.setPayTime(rechargePay.getPayDate());
        report.setFee(rechargePay.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setStatus(RechargeStatusEnum.PAID);
        report.setPayment(RechargePaymentEnum.valueOf(recharge.getPaymentId()));
        //充值佣金手续费
        RPCResponse<Long> response = commCommissionService.calcRechargeFee(userDO.getParentId(), rechargePay.getAmount(),recharge.getCoin());
        log.info("充值佣金手续费_response:{}",JSON.toJSONString(response));
        report.setCommFee(RPCResponseUtils.getData(response));
        report.setFirst(isFirst);
        report.setCreateTime(recharge.getCreateDate());
        report.setTimestamp(recharge.getCreateDate());
        report.setKeyword(recharge.getKeyword());
        report.setCoin(recharge.getCoin());

        reportService.rechargeReport(report);
    }

    /**
     * 累计商户充值总数
     *
     * @param recharge
     */
    private void addMerchantAccountCount(GlRecharge recharge) {
        GlPaymentMerchantaccount payment = glPaymentMerchantaccountMapper.selectByPrimaryKey(recharge.getMerchantId());
        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(),
                DateUtils.YYYYMMDD);
        GlPaymentMerchantaccount merchantCache = redisService.getHashObject(key, String.valueOf(payment.getMerchantId()),
                GlPaymentMerchantaccount.class);
        if (merchantCache == null) {
            payment.setSuccessAmount(0L);
            payment.setTotal(1);
            payment.setSuccess(0);
            redisService.putHashValue(key, String.valueOf(payment.getMerchantId()), payment);
            redisService.setTTL(key, DateConstant.SECOND.DAY);
        } else {
            payment.setSuccessAmount(merchantCache.getSuccessAmount());
            payment.setTotal(merchantCache.getTotal() + 1);
            payment.setSuccess(merchantCache.getSuccess());
            redisService.delHashValue(key, String.valueOf(payment.getMerchantId()));
            redisService.putHashValue(key, String.valueOf(payment.getMerchantId()), payment);
            redisService.setTTL(key, DateConstant.SECOND.DAY);
        }

        //用户充值失败队列
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppMapper.selectOneByEntity(recharge.getPaymentId(),
                recharge.getMerchantId(), null);
        if (null != merchantApp) {
            List<Integer> failList = new ArrayList<>();
            RedisResult<Integer> resultList = redisService.getListResult(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + recharge.getUserId(), Integer.class);
            if (resultList != null && !ObjectUtils.isEmpty(resultList.getListResult())) {
                failList = resultList.getListResult();
            }
            failList.remove(merchantApp.getId());
            failList.add(0, merchantApp.getId());
            if (failList.size() > 10) {
                failList = failList.subList(0, 10);
            }
            redisService.set(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + recharge.getUserId(), failList, DateConstant.SECOND.DAY);
        }
    }

    /**
     * 计算商户成功率
     *
     * @param recharge
     * @param amount
     * @param isApprove
     */
    private void addMerchantAccountSuccess(GlRecharge recharge, BigDecimal amount, boolean isApprove) {
        GlPaymentMerchantaccount payment = glPaymentMerchantaccountMapper.selectByPrimaryKey(recharge.getMerchantId());
        if (null != payment) {
            // 充值成功：成功数&充值金额 计数
            String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(recharge.getCreateDate(),
                    DateUtils.YYYYMMDD);
            GlPaymentMerchantaccount merchantCache = redisService.getHashObject(key, String.valueOf(payment.getMerchantId()),
                    GlPaymentMerchantaccount.class);
            if (merchantCache == null) {
                payment.setSuccessAmount(amount.longValue());
                payment.setTotal(1);
                // 补单审核通过-只累计今日收款金额、不累计成功数
                payment.setSuccess(isApprove == false ? 1 : 0);
                redisService.putHashValue(key, String.valueOf(payment.getMerchantId()), payment);
                redisService.setTTL(key, DateConstant.SECOND.DAY);
            } else {
                payment.setSuccessAmount(merchantCache.getSuccessAmount() + amount.longValue());
                payment.setTotal(merchantCache.getTotal());
                payment.setSuccess(isApprove == false ? merchantCache.getSuccess() + 1 : merchantCache.getSuccess());
                redisService.delHashValue(key, String.valueOf(payment.getMerchantId()));
                redisService.putHashValue(key, String.valueOf(payment.getMerchantId()), payment);
                redisService.setTTL(key, DateConstant.SECOND.DAY);
            }
        }

        // 预处理用户充值失败队列，notify成功后移除
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppMapper.selectOneByEntity(recharge.getPaymentId(),
                recharge.getMerchantId(), null);
        if (null != merchantApp) {
            List<Integer> failList = new ArrayList<>();
            RedisResult<Integer> resultList = redisService.getListResult(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + recharge.getUserId(), Integer.class);
            if (resultList != null && !ObjectUtils.isEmpty(resultList.getListResult())) {
                failList = resultList.getListResult();
            }
            failList.remove(merchantApp.getId());
            redisService.set(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + recharge.getUserId(), failList, DateConstant.SECOND.DAY);
        }
    }


    public void firstRechargeReportFix(GlRechargeDO recharge) {
        //充值记录上报
        RechargeReport rechargeReport = new RechargeReport();
        rechargeReport.setUuid(recharge.getOrderId());
        rechargeReport.setUid(recharge.getUserId());
        rechargeReport.setUserId(recharge.getUserId());
        rechargeReport.setCreateTime(recharge.getCreateDate());
        rechargeReport.setTimestamp(recharge.getCreateDate());
        rechargeReport.setPlatform(PlatformEnum.valueOf(recharge.getClientType()));
        rechargeReport.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        rechargeReport.setStatus(RechargeStatusEnum.valueOf(recharge.getStatus()));

        if (FundConstant.RechargeStatus.SUCCESS == recharge.getStatus()){
            Integer isFirst = glRechargeMapper.isFirstForFix(recharge.getUserId(), recharge.getCreateDate()) ? 1 : 0;
            rechargeReport.setFirst(isFirst);
        }
        log.info("充值记录重新上报 orderId = {} isFirst = {}", recharge.getOrderId(), rechargeReport.getFirst());
        reportService.rechargeReport(rechargeReport);
    }

    public void rechargeDataReport(GlRecharge recharge) {
        try {
            GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(recharge.getUserId()));

            //充值记录上报
            RechargeReport report = new RechargeReport();
            report.setUuid(recharge.getOrderId());
            report.setUid(recharge.getUserId());
            report.setUserId(recharge.getUserId());
            report.setPlatform(PlatformEnum.valueOf(recharge.getClientType()));
            report.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
            report.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
            report.setStatus(RechargeStatusEnum.valueOf(recharge.getStatus()));
            report.setUserName(recharge.getUsername());
            report.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
            report.setParentName(userDO.getParentName());
            report.setParentId(userDO.getParentId());
            report.setBank(BankEnum.valueOf(recharge.getBankId()));
            report.setMerchant(recharge.getMerchantId());
            report.setPayment(RechargePaymentEnum.valueOf(recharge.getPaymentId()));
            report.setSubType(FundConstant.paymentTypeMap.get(recharge.getPaymentId()));
            report.setRegTime(userDO.getRegisterDate());
            GlPaymentMerchantApp merchantApp = glPaymentMerchantAppMapper.selectOneByEntity(recharge.getPaymentId(),
                    recharge.getMerchantId(), null);
            if (null != merchantApp) {
                report.setMerchantCode(merchantApp.getMerchantCode());
                report.setChannelId(merchantApp.getChannelId());
                report.setChannelName(merchantApp.getChannelName());
            }

            report.setIsFake(userDO.getIsFake());

            //VIP等级
            if (userDO.getUserType().equals(UserConstant.UserType.PLAYER)) {
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
                report.setVipLevel(null == vipCache ? 0 : vipCache.getVipLevel());
            } else {
                report.setVipLevel(-1);
            }

            //用户层级
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
            report.setUserLevel(userlevel.getLevelId());
            report.setUserLevelName(userlevel.getName());

            GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != receiveInfo && recharge.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {
                BigDecimal usdtAmount = receiveInfo.getDigitalAmount();
                if (null != usdtAmount) {
                    usdtAmount = usdtAmount.multiply(BigDecimal.valueOf(100000000));
                } else {
                    usdtAmount = BigDecimal.ZERO;
                }

                BigDecimal rate = receiveInfo.getRate();
                if (null != rate) {
                    rate = rate.multiply(BigDecimal.valueOf(100000000));
                } else {
                    rate = BigDecimal.ZERO;
                }
                report.setUsdtAmount(usdtAmount.longValue());
                report.setRate(rate.longValue());
            }

            GlRechargePay rechargePay = glRechargePayMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != rechargePay) {
                report.setPayAmount(rechargePay.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setFinishTime(rechargePay.getPayDate());
                report.setPayTime(rechargePay.getPayDate());
                //充值佣金手续费
                RPCResponse<Long> response = commCommissionService.calcRechargeFee(userDO.getParentId(), rechargePay.getAmount(),recharge.getCoin());
                report.setCommFee(RPCResponseUtils.getData(response));
            }

//            report.setBalanceBefore(balance.subtract(rechargePay.getAmount()).multiply(BigDecimal.valueOf(10000)).longValue());
//            report.setBalanceAfter(balance.multiply(BigDecimal.valueOf(10000)).longValue());

            if (FundConstant.RechargeStatus.SUCCESS == recharge.getStatus()){
                Integer isFirst = glRechargeMapper.isFirstForFix(recharge.getUserId(), recharge.getCreateDate()) ? 1 : 0;
                report.setFirst(isFirst);
            }

            report.setCreateTime(recharge.getCreateDate());
            report.setTimestamp(recharge.getCreateDate());
            report.setCoin(recharge.getCoin());

            log.info("充值记录重新上报 report = {}", JSON.toJSONString(report));
            reportService.rechargeReport(report);

        } catch (GlobalException e) {
            log.info("获取 user 异常，OrderId = {}", recharge.getOrderId());
        }
    }

    public void rechargeDataReport(GlRecharge recharge, GlUserDO userDO) {
        try {

            //充值记录上报
            RechargeReport report = new RechargeReport();
            report.setUuid(recharge.getOrderId());
            report.setUid(recharge.getUserId());
            report.setUserId(recharge.getUserId());
            report.setPlatform(PlatformEnum.valueOf(recharge.getClientType()));
            report.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
            report.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
            report.setStatus(RechargeStatusEnum.valueOf(recharge.getStatus()));
            report.setUserName(recharge.getUsername());
            report.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
            report.setParentName(userDO.getParentName());
            report.setParentId(userDO.getParentId());
            report.setBank(BankEnum.valueOf(recharge.getBankId()));
            report.setMerchant(recharge.getMerchantId());
            report.setPayment(RechargePaymentEnum.valueOf(recharge.getPaymentId()));
            report.setSubType(FundConstant.paymentTypeMap.get(recharge.getPaymentId()));
            report.setRegTime(userDO.getRegisterDate());
            GlPaymentMerchantApp merchantApp = glPaymentMerchantAppMapper.selectOneByEntity(recharge.getPaymentId(),
                    recharge.getMerchantId(), null);
            if (null != merchantApp) {
                report.setMerchantCode(merchantApp.getMerchantCode());
                report.setChannelId(merchantApp.getChannelId());
                report.setChannelName(merchantApp.getChannelName());
            }

            report.setIsFake(userDO.getIsFake());

            //VIP等级
            if (userDO.getUserType().equals(UserConstant.UserType.PLAYER)) {
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
                report.setVipLevel(null == vipCache ? 0 : vipCache.getVipLevel());
            } else {
                report.setVipLevel(-1);
            }

            //用户层级
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
            report.setUserLevel(userlevel.getLevelId());
            report.setUserLevelName(userlevel.getName());

            GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != receiveInfo && recharge.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {
                BigDecimal usdtAmount = receiveInfo.getDigitalAmount();
                if (null != usdtAmount) {
                    usdtAmount = usdtAmount.multiply(BigDecimal.valueOf(100000000));
                } else {
                    usdtAmount = BigDecimal.ZERO;
                }

                BigDecimal rate = receiveInfo.getRate();
                if (null != rate) {
                    rate = rate.multiply(BigDecimal.valueOf(100000000));
                } else {
                    rate = BigDecimal.ZERO;
                }
                report.setUsdtAmount(usdtAmount.longValue());
                report.setRate(rate.longValue());
            }

            GlRechargePay rechargePay = glRechargePayMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != rechargePay) {
                report.setPayAmount(rechargePay.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setFinishTime(rechargePay.getPayDate());
                report.setPayTime(rechargePay.getPayDate());
                //充值佣金手续费
                RPCResponse<Long> response = commCommissionService.calcRechargeFee(userDO.getParentId(), rechargePay.getAmount(),recharge.getCoin());
                report.setCommFee(RPCResponseUtils.getData(response));

                report.setBalanceBefore(BigDecimal.ZERO.longValue());
                report.setBalanceAfter(rechargePay.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
            }

            if (FundConstant.RechargeStatus.SUCCESS == recharge.getStatus()){
                Integer isFirst = glRechargeMapper.isFirstForFix(recharge.getUserId(), recharge.getCreateDate()) ? 1 : 0;
                report.setFirst(isFirst);
            }

            report.setCreateTime(recharge.getCreateDate());
            report.setTimestamp(recharge.getCreateDate());
            report.setCoin(recharge.getCoin());

            log.info("充值记录重新上报 report = {}", JSON.toJSONString(report));
            reportService.rechargeReport(report);

        } catch (GlobalException e) {
            log.info("获取 user 异常，OrderId = {}", recharge.getOrderId());
        }
    }

    protected void setReceiveWalletEnable(String orderId) {
        GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(orderId);
        if (ObjectUtils.isEmpty(receiveInfo)) {
            return;
        }
        Integer receiveWalletId = receiveInfo.getReceiveWalletId();
        if (ObjectUtils.isEmpty(receiveWalletId)) {
            return;
        }
        DigitalReceiveWallet receiveWallet = digitalReceiveWalletMapper.selectByPrimaryKey(receiveWalletId);
        if (ObjectUtils.isEmpty(receiveWallet)) {
            return;
        }
        // 解除绑定
        digitalReceiveWalletMapper.unbind(receiveWallet.getId());
        // 收币钱包可用
        if (DigitalReceiveWalletStatusEnum.ENABLE.getStatus() != receiveWallet.getStatus()) {
            return;
        }
        RedisTools.setOperations().add(KeyConstant.DIGITAL.RECEIVE_WALLET_CACHE + receiveWallet.getCoin() + "_" + receiveWallet.getProtocol(), receiveWallet.getId());
    }

}