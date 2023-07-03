package com.seektop.fund.service.impl;

import com.alibaba.fastjson.JSON;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.RegexValidator;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.withdraw.*;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawGeneralConfig;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.controller.backend.dto.withdraw.config.GeneralConfigLimitDO;
import com.seektop.fund.dto.param.withdraw.RiskApproveDto;
import com.seektop.fund.dto.result.withdraw.GlWithdrawDO;
import com.seektop.fund.handler.C2COrderCallbackHandler;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.handler.WithdrawExceptionHandler;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawReturnRequestMapper;
import com.seektop.fund.mapper.GlWithdrawSplitMapper;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.model.GlWithdrawSplit;
import com.seektop.fund.service.GlWithdrawService;
import com.seektop.report.fund.WithdrawReport;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service(timeout = 5000, interfaceClass = GlWithdrawService.class, validation = "true")
@Slf4j
public class GlWithdrawServiceImpl implements GlWithdrawService {

    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @Resource
    private GlWithdrawBusiness withdrawBusiness;
    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    @Resource
    private GlWithdrawSplitMapper glWithdrawSplitMapper;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Autowired
    private WithdrawExceptionHandler withdrawExceptionHandler;
    @Resource
    private GlWithdrawConfigBusiness configBusiness;
    @Resource
    private GlWithdrawReturnRequestMapper returnRequestMapper;
    @Resource
    private GlWithdrawConfigBusiness withdrawConfigBusiness;
    @Resource(name = "withdrawHandler")
    private WithdrawHandler withdrawHandler;
    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService userService;
    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;
    @Resource
    private C2COrderHandler c2COrderHandler;
    @Resource
    private C2COrderCallbackHandler c2COrderCallbackHandler;
    @Resource
    private WithdrawApiRecordBusiness withdrawApiRecordBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private WithdrawExceptionBusiness withdrawExceptionBusiness;
    @Resource
    private RedisService redisService;
    @Resource
    private ReportService reportService;

    @Override
    public RPCResponse<Integer> countSuccessWithdrawByUser(Integer userId, Date createDate,String coinCode) {
        RPCResponse.Builder<Integer> newBuilder = RPCResponse.newBuilder();
        Integer count = glWithdrawMapper.countSuccessWithdrawByUser2(userId, createDate,coinCode);
        return newBuilder.success().setData(count).build();
    }

    @Override
    public RPCResponse<Map<String, Integer>> sumWithdrawCount(Integer userId, Date sTime, Date eTime,String coinCode) {
        RPCResponse.Builder<Map<String, Integer>> newBuilder = RPCResponse.newBuilder();
        Map<String, Integer> result = new HashMap<>();

        Integer normalCount = glWithdrawMapper.sumNormalWithdrawCount(userId, sTime, eTime,coinCode);

        Condition condition = new Condition(GlWithdraw.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn("status", Arrays.asList(FundConstant.WithdrawStatus.SUCCESS,
                FundConstant.WithdrawStatus.FORCE_SUCCESS, FundConstant.WithdrawStatus.RETURN_PART));
        criteria.andEqualTo("aisleType", 2);//大额提现
        if (null != userId) {
            criteria.andEqualTo("userId", userId);
        }
        if (null != coinCode) {
            criteria.andEqualTo("coin", coinCode);
        }
        if (null != sTime) {
            criteria.andGreaterThanOrEqualTo("createDate", sTime);
        }
        if (null != eTime) {
            criteria.andLessThanOrEqualTo("createDate", eTime);
        }
        List<GlWithdraw> glWithdrawList = glWithdrawMapper.selectByCondition(condition);
        Integer largeCount = 0;
        Map<String, String> parentId = new HashMap<>();
        if (glWithdrawList != null && glWithdrawList.size() != 0) {
            for (GlWithdraw glWithdraw : glWithdrawList) {
                GlWithdrawSplit split = glWithdrawSplitMapper.selectByPrimaryKey(glWithdraw.getOrderId());
                if (split != null) { //拆单只计数一笔
                    if (!parentId.containsKey(split.getParentId())) {
                        parentId.put(split.getParentId(), split.getParentId());
                        largeCount++;
                    }
                } else {
                    largeCount++;
                }
            }
        }
        result.put("normal", normalCount);
        result.put("large", largeCount);
        return newBuilder.success().setData(result).build();
    }

    @Override
    public RPCResponse<BigDecimal> sumWithdrawingTotal(Integer userId) {
        RPCResponse.Builder<BigDecimal> newBuilder = RPCResponse.newBuilder();

        Map<String, Object> param = new HashMap<>();
        param.put("userId", userId);
        param.put("startDate", null);
        param.put("endDate", null);
        param.put("status", Arrays.asList(FundConstant.WithdrawStatus.REVIEW_HOLD,
                FundConstant.WithdrawStatus.RISK_PENDING,
                FundConstant.WithdrawStatus.PENDING,
                FundConstant.WithdrawStatus.RETURN_PENDING,
                FundConstant.WithdrawStatus.SUCCESS_PENDING,
                FundConstant.WithdrawStatus.AUTO_FAILED));
        BigDecimal amount = glWithdrawMapper.getWithdrawAmountTotal(param);

        //提现部分退回
        param.put("withdrawStatus", FundConstant.WithdrawStatus.RETURN_PART_PENDING);
        param.remove("status");
        amount = amount.add(returnRequestMapper.getAmountTotal(param));

        return newBuilder.success().setData(amount).build();
    }

    @Override
    public RPCResponse<List<GlWithdrawDO>> findWithdrawReturnList() {
        Condition condition = new Condition(GlWithdraw.class);
        Condition.Criteria criteria = condition.createCriteria();
        Date now = new Date();
        criteria.andEqualTo("merchant", "StormPay");
        criteria.andEqualTo("status", FundConstant.WithdrawStatus.AUTO_PENDING);
        criteria.andBetween("createDate", DateUtils.getStartWithCurrentDay(now), DateUtils.getEndWithCurrentDay(now));

        List<GlWithdraw> result = glWithdrawMapper.selectByCondition(condition);

        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(result, GlWithdrawDO.class));
    }

    @Override
    public RPCResponse<Void> manualReturnWithdraw(GlWithdrawDO withdrawDO) throws GlobalException {
        RPCResponse.Builder<Void> newBuilder = RPCResponse.newBuilder();
        glWithdrawBusiness.manualWithdrawReturn(withdrawDO.getOrderId());
        return newBuilder.success().build();
    }

    @Override
    public RPCResponse<GlWithdrawDO> findWithdrawById(String orderId) {
        GlWithdraw withdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (ObjectUtils.isEmpty(withdraw)) {
            return RPCResponseUtils.buildSuccessRpcResponse(null);
        }
        if (ObjectUtils.isEmpty(withdraw.getMerchantId())) {
            return RPCResponseUtils.buildSuccessRpcResponse(null);
        }
        // 查询提现订单对应的商户信息
        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountBusiness.findById(withdraw.getMerchantId());
        if (ObjectUtils.isEmpty(withdrawMerchantAccount)) {
            return RPCResponseUtils.buildSuccessRpcResponse(null);
        }
        GlWithdrawDO withdrawDO = DtoUtils.transformBean(withdraw, GlWithdrawDO.class);
        withdrawDO.setChannelId(withdrawMerchantAccount.getChannelId());
        withdrawDO.setChannelName(withdrawMerchantAccount.getChannelName());
        return RPCResponseUtils.buildSuccessRpcResponse(withdrawDO);
    }

    @Override
    public RPCResponse<List<GlWithdrawDO>> findByOrderIds(List<String> orderIds) {
        List<GlWithdraw> list = withdrawBusiness.findByOrderIds(orderIds);
        List<GlWithdrawDO> withdrawDOList = Lists.newArrayList();
        list.stream().forEach(obj -> {
            GlWithdrawDO glWithdrawDO = DtoUtils.transformBean(obj, GlWithdrawDO.class);
            if (obj.getAisleType() == 4) {
                C2COrderDetailResult detailResult = c2COrderHandler.getByWithdrawOrderId(obj.getOrderId(), obj.getThirdOrderId());
                Optional.ofNullable(detailResult).ifPresent(e -> {
                    glWithdrawDO.setPaymentDate(e.getPaymentDate());
                });
            }
            withdrawDOList.add(glWithdrawDO);
        });
        return RPCResponseUtils.buildSuccessRpcResponse(withdrawDOList);
    }

    @Override
    public RPCResponse<Boolean> doWithdrawRiskApprove(RiskApproveDto riskApproveDto) throws GlobalException {
        withdrawExceptionHandler.approve(riskApproveDto);
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }

    public RPCResponse<BigDecimal> getWithdrawingTotal(Integer userId) {
        BigDecimal amount = withdrawBusiness.getWithdrawingTotal(userId);
        return RPCResponseUtils.buildSuccessRpcResponse(amount);
    }

    @Override
    public RPCResponse<Void> fixData(Date startDate, Date endDate) {
        withdrawBusiness.withdrawDataFix(startDate, endDate);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    public RPCResponse<List<String>> findOrderIds(Integer userId, Integer status, Date sTime, Date eTime) {
        Condition condition = new Condition(GlWithdraw.class);
        condition.selectProperties("orderId").createCriteria().andEqualTo("userId",userId)
                .andEqualTo("status",status).andBetween("createDate",sTime,eTime);
        List<GlWithdraw> byCondition = withdrawBusiness.findByCondition(condition);
        List<String> collect = byCondition.stream().map(GlWithdraw::getOrderId).collect(Collectors.toList());
        return RPCResponseUtils.buildSuccessRpcResponse(collect);
    }

    @Override
    public RPCResponse<Void> syncWithdraw(String channelIds) throws GlobalException {
        withdrawBusiness.syncWithdraw(channelIds);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }


    @Override
    @Transactional(rollbackFor = GlobalException.class)
    public RPCResponse<Boolean> unMatch() throws GlobalException {
        C2CConfigDO config = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (ObjectUtils.isEmpty(config)) {
            return RPCResponseUtils.buildSuccessRpcResponse(false);
        }

        List<GlWithdraw> list = withdrawBusiness.findExpired(config.getMatchWaitTime(), FundConstant.WithdrawStatus.AUTO_PENDING, 4);


        List<GlWithdraw> failedList = Lists.newArrayList();
        List<GlWithdraw> normalList = Lists.newArrayList();
        Date now = new Date();
        for (GlWithdraw withdraw: list) {
            GlWithdrawGeneralConfig generalConfig = withdrawConfigBusiness.getWithdrawGeneralConfig(withdraw.getCoin());
            if (null == generalConfig) {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }

            //防止重复执行
            String key = "withdrawUnMatchJob_" + withdraw.getOrderId();
            Long count = redisService.incrBy(key, 1);
            if (count.intValue() > 1) {
                log.info("订单已执行过：{}", withdraw.getOrderId());
                redisService.setTTL(key,60);
                continue;
            }
            //校验提现订单是否已撮合
            if (c2COrderHandler.checkWithdrawIsMatching(withdraw.getOrderId()) == false) {
                log.info("订单号：{}已撮合",withdraw.getOrderId());
                continue;
            }
            //从撮合池子里删除
            try {
                boolean result = c2COrderCallbackHandler.withdrawCancel(withdraw.getOrderId(), false);
                log.info("定位撮合成功删除提现订单_删除结果:{},order:{}",result, withdraw.getOrderId());
                if (result == false) {
                    continue;
                }
            }catch (GlobalException e) {
                log.error("取消撮合提现订单异常", e);
                continue;
            }
            //提现类型改为 普通提现
            withdraw.setAisleType(1);
            withdraw.setStatus(FundConstant.WithdrawStatus.PENDING);

            //重新计算手续费
            GlUserDO userDO = RPCResponseUtils.getData(userService.findById(withdraw.getUserId()));
            setWithdrawFee(userDO, now, withdraw, generalConfig);

            //校验金额手续费
            String message = validateGeneral(userDO.getId(), generalConfig, withdraw.getAmount(), now);
            if (StringUtils.isNotEmpty(message)) {
                log.info("提现重新下单异常:{}",message);
                withdraw.setStatus(FundConstant.WithdrawStatus.FAILED);
                withdraw.setRemark("极速提现转普通提现失败：" + message);
                withdraw.setRejectReason(message);

                failedList.add(withdraw);
                continue;
            }

            //提现分单-选择商户-出款方式
            withdraw.setMerchantId(null);
            withdraw.setMerchant(null);
            withdraw.setMerchantCode(null);
            withdraw.setRemark(null);
            withdraw.setStatus(FundConstant.WithdrawStatus.PENDING);
            withdraw.setSeperateDate(now);
            withdraw.setSeperateCreator("系统自动|System");
            withdraw.setWithdrawType(FundConstant.WithdrawType.All);//默认出款方式为全部
            if ( withdraw.getBankId() != FundConstant.PaymentType.DIGITAL_PAY) {
                // 提现银行卡姓名(true纯中文、false含非中文字符)
                if (RegexValidator.isChinese(withdraw.getName())) {
                    String level = withdraw.getUserLevel();
                    withdraw = glWithdrawBusiness.setWithdrawManual(withdraw, level);
                } else {
                    withdraw.setSeperateReason("非纯中文姓名");
                }
            } else {
                withdraw = glWithdrawBusiness.setWithdrawManual(withdraw, withdraw.getUserLevel());
            }
            log.info("unmatch_withdraw:{}", JSON.toJSONString(withdraw));
            redisService.setTTL(key, 600);
            withdrawBusiness.updateByPrimaryKey(withdraw);

            //出款失败 - 删除API调用记录
            withdrawApiRecordBusiness.deleteById(withdraw.getOrderId());

            normalList.add(withdraw);
        }

        //转普通提现失败，退回金额，上报
        GlAdminDO admin = new GlAdminDO();
        admin.setUserId(0);
        admin.setUsername("系统|System");

        WithdrawExceptionApproveDto approveDto = new WithdrawExceptionApproveDto();
        approveDto.setStatus(2);
        approveDto.setRemark("极速提现转普通提现失败");
        approveDto.setRejectReason("极速提现转普通提现失败");
        approveDto.setUpdateTime(new Date());
        approveDto.setC2cToNormal(true);

        withdrawExceptionBusiness.doWithdrawRiskApprove(failedList,approveDto, admin);

        //转普通提现-发送自动出款消息
        for (GlWithdraw withdraw : normalList) {
            if (ObjectUtils.isNotEmpty(withdraw.getMerchantId())) {
                glWithdrawBusiness.sendWithdrawMsg(withdraw);
            }
            //提现上报
            report(withdraw);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }


    private void setWithdrawFee(GlUserDO userDO, Date now, GlWithdraw withdraw, GlWithdrawGeneralConfig generalConfig ) {
        // 普通提现：已使用免费次数
        int generalFreeWithdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), 0, 1, withdraw.getCoin());
        // True 免费
        Boolean freeWithdraw = generalFreeWithdrawCount < generalConfig.getFreeTimes();

        // 提现手续费
        BigDecimal withdrawFee = BigDecimal.ZERO;
        if (!freeWithdraw) {
            if ("fix".equals(generalConfig.getFeeType())) {
                withdrawFee = generalConfig.getFee();
            } else {
                withdrawFee = withdraw.getAmount().multiply(generalConfig.getFee()).divide(BigDecimal.valueOf(100));
                if (withdrawFee.compareTo(generalConfig.getFeeLimit()) == 1) {
                    withdrawFee = generalConfig.getFeeLimit();
                }
            }
            withdraw.setFreeStatus(1);
        } else {
            withdraw.setFreeStatus(0);
        }
        withdraw.setFee(withdrawFee);
    }

    private static DecimalFormat decimalFormat = new DecimalFormat("0.00");
    private String validateGeneral(Integer userId, GlWithdrawGeneralConfig generalConfig, BigDecimal amount, Date now) {
        // 已提现次数
        int generalWithdrawCount = glWithdrawBusiness.getWithdrawCount(userId, DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 1, generalConfig.getCoin());
        //银行卡提现校验金额
        if (amount.compareTo(generalConfig.getMinLimit()) == -1) {
            String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(generalConfig.getMinLimit()));
            return message;
        }
        if (amount.compareTo(generalConfig.getMaxLimit()) == 1) {
            String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(generalConfig.getMaxLimit()));
            return message;
        }
        if (generalWithdrawCount >= generalConfig.getCountLimit()) {
            String message = String.format("今日已提现 %d 次,提现次数达到上限.", generalWithdrawCount);
            return message;
        }
        String feeType = "fix";
        if (feeType.equals(generalConfig.getFeeType())) {
            if (amount.compareTo(generalConfig.getFee()) <= 0) {
                String message = String.format("提现金额不能低于固定手续费 %s元", decimalFormat.format(generalConfig.getFeeType()));
                return message;
            }
        }
        //普通提现根据用户层级验证提现限额
        if (!CollectionUtils.isEmpty(generalConfig.getLimitList())) {
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userId);
            if (userlevel.getWithdrawOff() == 1) {
                return  "当前会员层级不可提现,请联系客服.";
            }
            for (GeneralConfigLimitDO obj: generalConfig.getLimitList()) {
                if (obj.getLevelIds().contains(userlevel.getLevelId())
                        && (amount.compareTo(obj.getMinAmount()) < 0
                        || amount.compareTo(obj.getMaxAmount()) > 0)) {
                    return String.format("您的提现金额区间为%s - %s",decimalFormat.format(obj.getMinAmount()),decimalFormat.format(obj.getMaxAmount()));
                }
            }
        }
        return null;
    }

    private void report(GlWithdraw withdraw) {
        WithdrawReport report = new WithdrawReport();
        report.setUuid(withdraw.getOrderId());
        report.setFee(withdraw.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setAmountNet(withdraw.getAmount().subtract(withdraw.getFee()).multiply(new BigDecimal(100000000)).longValue());
        reportService.withdrawReport(report);
    }


    public RPCResponse<List<Integer>> getRiskType(GlWithdrawDO glWithdrawDO, GlUserDO userDO, Date now)  {
        List<Integer> riskTypes = Lists.newArrayList();
        try{
            com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO withdrawDO =
                    DtoUtils.transformBean(glWithdrawDO,com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO.class);
            riskTypes =  glWithdrawBusiness.getRiskType(withdrawDO, userDO, now);
        } catch (GlobalException e) {
            log.error("查询提现风控类型异常:{}",e);
        }
        return  RPCResponseUtils.buildSuccessRpcResponse(riskTypes);
    }
}