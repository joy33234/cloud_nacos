package com.seektop.fund.business.recharge;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.controller.backend.param.recharge.RechargeApproveDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeRequestDO;
import com.seektop.fund.controller.backend.result.recharge.*;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.handler.ReportExtendHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlPaymentHandler;
import com.seektop.fund.payment.GlRechargeHandlerManager;
import com.seektop.fund.payment.RechargeNotify;
import com.seektop.system.service.GlSystemDepartmentJobService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Slf4j
@Component
public class GlRechargeManageBusiness {

    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Reference(retries = 2, timeout = 3000)
    private GlSystemDepartmentJobService glSystemDepartmentJobService;

    @Autowired
    private GlRechargeMapper glRechargeMapper;

    @Resource
    private RedisService redisService;

    @Resource
    private ReportService reportService;

    @Resource
    private ReportExtendHandler reportExtendHandler;

    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantaccountBusiness;

    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;

    @Resource
    private GlPaymentMerchantFeeBusiness glPaymentMerchantFeeBusiness;

    @Resource
    private GlRechargeSuccessRequestBusiness glRechargeSuccessRequestBusiness;

    @Resource
    private GlRechargeSuccessApproveBusiness glRechargeSuccessApproveBusiness;

    @Resource
    private GlRechargeTransactionBusiness glRechargeTransactionBusiness;

    @Resource
    private GlRechargeRelationBusiness glRechargeRelationBusiness;

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private GlRechargePayBusiness glRechargePayBusiness;

    @Resource
    private GlRechargeHandlerManager glRechargeHandlerManager;

    @Resource
    private GlRechargeReceiveInfoBusiness glRechargeReceiveInfoBusiness;
    @Resource
    private C2COrderHandler c2COrderHandler;

    /**
     * 查询充值订单详情
     *
     * @param orderId
     * @return
     * @throws GlobalException
     */
    public RechargeDetailResult loadDetail(String orderId, GlAdminDO adminDO) throws GlobalException {
        log.info("查询充值订单详情 ->{}", adminDO.getUsername());
        GlRecharge recharge = glRechargeMapper.selectByPrimaryKey(orderId);
        if (null == recharge) {
            throw new GlobalException(ResultCode.DATA_ERROR, "充值订单查询失败");
        }
        RechargeDetailResult detailDto = new RechargeDetailResult();
        //附件
        List<String> attachments = Lists.newArrayList();
        //充值订单
        GlRechargeDO rechargeDO = DtoUtils.transformBean(recharge, GlRechargeDO.class);
        //关联订单信息
        GlRechargeRelation rechargeRelation = glRechargeRelationBusiness.findById(rechargeDO.getOrderId());
        if (null != rechargeRelation) {
            rechargeDO.setOriginalOrderId(rechargeRelation.getRelationOrderId());
            if (StringUtils.isNotBlank(rechargeRelation.getImg())) {
                String[] reqImgStr = rechargeRelation.getImg().split("\\|");
                attachments.addAll(Lists.newArrayList(reqImgStr));
            }
        }
        //用户层级名称
        extendRechargeLevelName(Lists.newArrayList(rechargeDO));
        //脱敏处理
        rechargeDO.setCardNo(Encryptor.builderBankCard().doEncrypt(rechargeDO.getCardNo()));
        rechargeDO.setCardUsername(Encryptor.builderName().doEncrypt(rechargeDO.getCardUsername()));
        String keyWord = StringUtils.isEmpty(rechargeDO.getKeyword()) ? "" : rechargeDO.getKeyword().split("\\|")[0];
        rechargeDO.setKeyword(Encryptor.builderName().doEncrypt(keyWord));
        rechargeDO.setPaymentName(FundConstant.paymentTypeMap.get(rechargeDO.getPaymentId()));

        detailDto.setRecharge(rechargeDO);

        //根据关联单号创建的订单信息
        List<GlRechargeRelation> rechargeRelations = glRechargeRelationBusiness.findAllBy("relationOrderId", orderId);
        List<String> relOrderList = rechargeRelations.stream().map(GlRechargeRelation::getOrderId).collect(Collectors.toList());

        String relOrderIds = "'" + StringUtils.join(relOrderList, "','") + "'";
        List<GlRecharge> relRechargeList = glRechargeMapper.selectByIds(relOrderIds);
        List<GlRechargeDO> rechargeDOList = DtoUtils.transformList(relRechargeList, GlRechargeDO.class);
        //用户层级名称
        extendRechargeLevelName(rechargeDOList);
        detailDto.setRelationRecharge(rechargeDOList);

        //充值订单支付信息 RechargePay
        GlRechargePay pay = glRechargePayBusiness.findById(orderId);
        if (null != pay) {
            detailDto.setRechargePay(DtoUtils.transformBean(pay, RechargePayResult.class));
        }

        GlRechargeSuccessRequest reqRecharge = glRechargeSuccessRequestBusiness.findById(orderId);
        if (null != reqRecharge) {
            detailDto.setReqRecharge(DtoUtils.transformBean(reqRecharge, RechargeSuccessRequestResult.class));
            String reqImg = reqRecharge.getReqImg();
            if (reqImg != null) {
                String[] reqImgStr = reqImg.split("\\|");
                attachments.addAll(Lists.newArrayList(reqImgStr));
                List<String> delList = Lists.newArrayList("null");
                attachments.removeAll(delList);
            }
        }
        detailDto.setAttachments(attachments);

        GlRechargeSuccessApprove apvRecharge = glRechargeSuccessApproveBusiness.findById(orderId);
        if (null != apvRecharge) {
            detailDto.setApvRecharge(DtoUtils.transformBean(apvRecharge, RechargeSuccessApproveResult.class));
        }

        GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoBusiness.findById(orderId);
        if (null != receiveInfo) {
            detailDto.setThirdOrderId(receiveInfo.getThirdOrderId());
            rechargeDO.setWithdrawUserName(receiveInfo.getKeyword());
            if (recharge.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {
                RechargeDigitalResult digitalResult = new RechargeDigitalResult();
                digitalResult.setUsdtAmount(receiveInfo.getDigitalAmount());
                digitalResult.setRate(receiveInfo.getRate());
                digitalResult.setRealRate(receiveInfo.getRealRate());
                digitalResult.setProtocol(receiveInfo.getProtocol());
                digitalResult.setBlockAddress(receiveInfo.getBlockAddress());
                digitalResult.setTxHash(receiveInfo.getTxHash());
                if (StringUtils.isNotEmpty(receiveInfo.getKeyword())) {
                    try {
                        digitalResult.setUsdtPayAmount(new BigDecimal(receiveInfo.getKeyword()));
                    } catch (Exception e) {
                        log.error("转换实际USDT数量异常",receiveInfo.getKeyword());
                    }
                }
                detailDto.setDigitalResult(digitalResult);
            }
        }


        //ture ：转帐接口  false: 充值接口
        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(recharge.getMerchantId());
        if (merchantAccount != null && (Objects.equals(FUND_COMMON_ON, merchantAccount.getEnableScript())
                || merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay)) {
            GlPaymentHandler handler = glRechargeHandlerManager.getPaymentHandler(merchantAccount);
            if (null != handler) {
                detailDto.setInnerPay(handler.innerPay(merchantAccount, recharge.getPaymentId()));
            }
        } else {
            detailDto.setInnerPay(innerPay(merchantAccount, recharge));
        }
        if (ObjectUtils.isNotEmpty(merchantAccount)
                && merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
            C2COrderDetailResult orderDetailResult = c2COrderHandler.getByRechargeOrderId(orderId);
            Optional.ofNullable(orderDetailResult).ifPresent(obj -> {
                detailDto.setPendingConfirmDate(obj.getPaymentDate());
            });
        }
        return detailDto;
    }

    private boolean innerPay(GlPaymentMerchantaccount merchantAccount, GlRecharge recharge) {
        //银行卡转账
        if (recharge.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER &&
                (merchantAccount.getChannelId() == FundConstant.PaymentChannel.JINPAY
                        || merchantAccount.getChannelId() == FundConstant.PaymentChannel.STORMPAY
                        || merchantAccount.getChannelId() == FundConstant.PaymentChannel.STPAYER
                        || merchantAccount.getChannelId() == FundConstant.PaymentChannel.XIANGYUNPAY)) {
            return true;
        }
        //支付宝转账
        if (recharge.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER &&
                (merchantAccount.getChannelId() == FundConstant.PaymentChannel.JINPAY
                        || merchantAccount.getChannelId() == FundConstant.PaymentChannel.XIANGYUNPAY)) {
            return true;
        }
        //云闪付转账
        if (recharge.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER &&
                (merchantAccount.getChannelId() == FundConstant.PaymentChannel.JINPAY
                        || merchantAccount.getChannelId() == FundConstant.PaymentChannel.XIANGYUNPAY)) {
            return true;
        }
        return false;
    }

    private void extendRechargeLevelName(List<GlRechargeDO> recharges) {
        for (GlRechargeDO recharge : recharges) {
            try {
                Integer userLevel = 1;
                if (!StringUtils.isBlank(recharge.getUserLevel())) {
                    userLevel = Integer.parseInt(recharge.getUserLevel());
                }
                GlFundUserlevel glFundUserlevel = glFundUserlevelBusiness.findById(userLevel);
                recharge.setUserLevelName(glFundUserlevel.getName());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

    }

    /**
     * 查询超时订单列表
     * @param minutes 分钟
     *
     * @return
     */
    public List<GlRecharge> findExpiredList(int minutes, Integer subStatus, Integer channelId ) {
        Condition con = new Condition(GlRecharge.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("status", 0).andLessThan("createDate", org.apache.commons.lang3.time.DateUtils.addMinutes(new Date(), -minutes));
        if (ObjectUtils.isNotEmpty(subStatus)) {
            criteria.andNotEqualTo("subStatus", subStatus);
        }
        if (ObjectUtils.isNotEmpty(channelId)) {
            criteria.andEqualTo("channelId", channelId);
        }
        con.setOrderByClause("create_date asc");
        return glRechargeMapper.selectByCondition(con);
    }

    /**
     * 充值订单：充值补单申请
     *
     * @param glAdminDO
     * @param requestDO
     * @throws GlobalException
     */
    public void requestRecharge(GlAdminDO glAdminDO, RechargeRequestDO requestDO) throws GlobalException {
        if (requestDO.getAttachments().size() > 3) {
            throw new GlobalException(ResultCode.DATA_ERROR, "图片最多只能上传3张.");
        }
        GlRechargeSuccessRequest dbReq = glRechargeSuccessRequestBusiness.findById(requestDO.getOrderId());
        if (dbReq != null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "已申请充值补单");
        }
        glRechargeTransactionBusiness.requestRecharge(glAdminDO, requestDO);
    }

    /**
     * 充值补单审核
     *
     * @param glAdminDO
     * @param approveDO
     * @return
     * @throws GlobalException
     */
    public void requestRechargeApprove(GlAdminDO glAdminDO, RechargeApproveDO approveDO, Integer systemId) throws GlobalException {
        log.info("approveDO:{}", JSON.toJSONString(approveDO));
        GlRecharge req = glRechargeBusiness.findById(approveDO.getOrderId());
        if (null == req) {
            throw new GlobalException(ResultCode.DATA_ERROR, "充值数据查询失败");
        }
        checkJobPression(req.getLimitType(), systemId, glAdminDO.getJobId());
        GlRechargeSuccessRequest dbReq = glRechargeSuccessRequestBusiness.findById(approveDO.getOrderId());
        if (null == dbReq || dbReq.getStatus() != FundConstant.ChangeReqStatus.PENDING_APPROVAL) {
            throw new GlobalException(ResultCode.DATA_ERROR, "补单审核申请记录不存在或已审核完成");
        }

        GlRechargeSuccessApprove dbApv = glRechargeSuccessApproveBusiness.findById(approveDO.getOrderId());
        if (null != dbApv) {
            throw new GlobalException(ResultCode.DATA_ERROR, "补单审核已完成，不能重复审核");
        }

        glRechargeTransactionBusiness.doRechargeApprove(glAdminDO, approveDO);
    }

    private void checkJobPression(Integer limitType, Integer systemId, Integer jobId) throws GlobalException {
        RPCResponse<List<Long>> rpcJob = glSystemDepartmentJobService.findMenuListByJobIdAndSystemId(jobId, systemId);
        List<Long> jobMenu = RPCResponseUtils.getData(rpcJob);
        if (null == jobMenu) {
            throw new GlobalException(ResultCode.DATA_ERROR, "岗位权限不足");
        }
        if ((limitType == 0 && !jobMenu.contains(10030000L))
                || (limitType == 1 && !jobMenu.contains(10030000L))) {
            throw new GlobalException(ResultCode.DATA_ERROR, "岗位权限不足");
        }
    }

    /**
     * 查询充值订单-商户后台状态
     *
     * @param orderId
     * @return
     * @throws GlobalException
     */
    public RechargePayResult queryRechargeOrder(String orderId) throws GlobalException {
        GlRecharge recharge = glRechargeMapper.selectByPrimaryKey(orderId);
        if (null == recharge) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        RechargePayResult payResult = null;
        if (recharge.getStatus() == ProjectConstant.RechargeStatus.SUCCESS) {
            payResult = new RechargePayResult();
            payResult.setOrderId(recharge.getOrderId());
            payResult.setStatus(1);
            redisService.set(RedisKeyHelper.RECHARGE_MERCHANT_ORDER_STATUS_ + recharge.getOrderId(), payResult, 120);
            return payResult;
        }
        if (!glRechargeHandlerManager.supportQuery(recharge.getChannelId())) {
            payResult = new RechargePayResult();
            payResult.setOrderId(recharge.getOrderId());
            payResult.setStatus(0);
            redisService.set(RedisKeyHelper.RECHARGE_MERCHANT_ORDER_STATUS_ + recharge.getOrderId(), payResult, 120);
            return payResult;
        }

        payResult = redisService.get(RedisKeyHelper.RECHARGE_MERCHANT_ORDER_STATUS_ + recharge.getOrderId(), RechargePayResult.class);
        if (payResult == null) {

            RechargeNotify notify = glRechargeBusiness.doRechargeOrderQuery(recharge);
            payResult = new RechargePayResult();
            if (notify == null) {
                //待支付
                payResult.setStatus(0);
            } else {
                //支付成功
                payResult.setStatus(1);
            }
            redisService.set(RedisKeyHelper.RECHARGE_MERCHANT_ORDER_STATUS_ + recharge.getOrderId(), payResult, 120);
        }
        return payResult;
    }
}

