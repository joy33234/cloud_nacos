package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.github.pagehelper.PageHelper;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.common.utils.RegexValidator;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.withdraw.RiskWithdrawInfoParamDO;
import com.seektop.data.result.withdraw.RiskWithdrawResult;
import com.seektop.data.service.WithdrawService;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.NameTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.business.GlWithdrawRecordBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawPolicyAmountConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawPolicyConfig;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestDO;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawAlarmDto;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawResult;
import com.seektop.fund.controller.backend.result.withdraw.WithdrawBankSettingResult;
import com.seektop.fund.customer.WithdrawSender;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.mapper.*;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.GlWithdrawHandlerManager;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.vo.GlWithdrawQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import com.seektop.gamebet.dto.param.GlTransferSearchParamDo;
import com.seektop.gamebet.service.GameTransferService;
import com.seektop.gamebet.service.GameUserService;
import com.seektop.report.fund.WithdrawMessage;
import com.seektop.report.fund.WithdrawReport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GlWithdrawBusiness extends AbstractBusiness<GlWithdraw> {


    @DubboReference(retries = 2, timeout = 3000)
    private WithdrawService withdrawService;

    @DubboReference(retries = 2, timeout = 3000)
    private GameUserService gameUserService;

    @DubboReference(retries = 2, timeout = 3000)
    private GameTransferService gameTransferService;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    @Resource
    private GlWithdrawHandlerManager glWithdrawHandlerManager;

    @Resource
    private GlWithdrawApiRecordMapper glWithdrawApiRecordMapper;

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawSplitMapper glWithdrawSplitMapper;

    @Resource
    private WithdrawSender withdrawSender;

    @Resource
    private GlWithdrawApiLogMapper glWithdrawApiLogMapper;

    @Resource
    private GlWithdrawConditionBusiness glWithdrawConditionBusiness;

    @Resource
    private GlWithdrawAutoConditionBusiness glWithdrawAutoConditionBusiness;
    @Autowired
    private WithdrawAutoConditionMerchantAccountBusiness conditionMerchantAccountBusiness;

    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    @Resource
    private GlWithdrawLevelConfigBusiness glWithdrawLevelConfigBusiness;

    @Resource
    private GlWithdrawReturnRequestMapper glWithdrawReturnRequestMapper;

    @Resource
    private GlWithdrawTransactionalBusiness glWithdrawTransactionalBusiness;

    @Resource
    private WithdrawAlarmBusiness withdrawAlarmBusiness;

    @Resource
    private GlWithdrawApproveBusiness withdrawApproveBusiness;

    @Resource
    private GlWithdrawReturnRequestBusiness withdrawReturnRequestBusiness;

    @Autowired
    private ExecutorService executorService;

    @Resource
    private GlWithdrawBankBusiness glWithdrawBankBusiness;

    @Autowired
    private GlWithdrawReceiveInfoMapper glWithdrawReceiveInfoMapper;

    @Resource
    private UserVipUtils userVipUtils;

    @Resource
    private UserFundUtils userFundUtils;

    @Resource
    private ReportService reportService;

    @Resource
    private GlWithdrawRecordBusiness glWithdrawRecordBusiness;

    @Resource
    private GlWithdrawReturnRequestMapper returnRequestMapper;

    @Resource
    private GlFundBusiness glFundBusiness;

    public void withdrawDataFix(Date startDate, Date endDate) {
        try {
            Integer page = 1;
            List<GlWithdraw> withdrawList = getFixData(startDate, endDate, page, 500);
            while (CollectionUtils.isEmpty(withdrawList) == false) {
                log.error("第{}次修复数据,总量是{}", page, withdrawList.size());
                for (GlWithdraw withdraw : withdrawList) {
                    WithdrawReport report = new WithdrawReport();
                    report.setUuid(withdraw.getOrderId());
                    report.setOrderId(withdraw.getOrderId());
                    report.setTimestamp(withdraw.getCreateDate());
                    report.setLastUpdate(withdraw.getLastUpdate());
                    // 用户VIP等级
                    UserVIPCache vipCache = userVipUtils.getUserVIPCache(withdraw.getUserId());
                    if (ObjectUtils.isEmpty(vipCache) == false) {
                        report.setVipLevel(vipCache.getVipLevel());
                        report.set("vipLevel", vipCache.getVipLevel());
                    }
                    // 用户层级
                    FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(withdraw.getUserId());
                    if (ObjectUtils.isEmpty(userLevel) == false) {
                        report.setUserLevel(userLevel.getLevelId());
                        report.setUserLevelName(userLevel.getLevelName());
                    }
                    reportService.withdrawReport(report);
                }
                page = page + 1;
                withdrawList = getFixData(startDate, endDate, page, 500);
            }
        } catch (Exception ex) {
            log.error("修复提现数据发生异常", ex);
        }
    }

    protected List<GlWithdraw> getFixData(Date startDate, Date endDate, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(GlWithdraw.class);
        if (startDate != null && endDate != null) {
            condition.createCriteria().andBetween("createDate", startDate, endDate);
        }
        return findByCondition(condition);
    }

    /**
     * 提现订单回调
     *
     * @param merchant
     * @param resMap
     * @return
     * @throws GlobalException
     */
    public WithdrawNotify doWithdrawNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        try {
            GlPaymentWithdrawHandler handler = glWithdrawHandlerManager.getPaymentWithdrawHandler(merchant);
            if (handler == null) {
                log.error("no_handler_for_withdraw_channel {}.", merchant.getChannelId());
                return null;
            }
            WithdrawNotify notify = handler.doTransferNotify(merchant, resMap);
            if (null == notify) {
                return null;
            }
            return notify;
        } catch (Exception e) {
            log.error("do_withdraw_notify_error", e);
            throw new GlobalException(e);
        }
    }


    /**
     * 获取用户最后提现记录
     *
     * @param userId
     * @return
     */
    public List<GlWithdraw> getLastWithdrawList(Integer userId) {
        List<GlWithdraw> result = new ArrayList<>();
        GlWithdraw withdraw = getLastWithdraw(userId);
        if (withdraw == null) {
            return null;
        }
        // 根据最后一笔订单记录  判断是否拆单
        GlWithdrawSplit withdrawSplit = glWithdrawSplitMapper.selectByPrimaryKey(withdraw.getOrderId());
        if (withdrawSplit == null) {
            result.add(withdraw);
            return result;
        }

        //查询同批次拆单记录
        GlWithdrawSplit condition = new GlWithdrawSplit();
        condition.setParentId(withdrawSplit.getParentId());
        List<GlWithdrawSplit> withdrawSplitList = glWithdrawSplitMapper.select(condition);
        for (GlWithdrawSplit split : withdrawSplitList) {
            GlWithdraw glWithdraw = findById(split.getOrderId());
            result.add(glWithdraw);
        }
        return result;
    }


    /**
     * 封装提现订单数据
     *
     * @param withdrawDO
     * @param userDO
     * @param userCard
     * @param level
     * @return
     * @throws GlobalException
     */
    public GlWithdraw doUserWithdraw(GlWithdrawDO withdrawDO, GlUserDO userDO, GlWithdrawUserBankCard userCard,
                                     GlWithdrawUserUsdtAddress usdtAddress,
                                     GlFundUserlevel level) throws GlobalException {

        String orderId = redisService.getTradeNo("TX");
        Date now = new Date();

        //用户提现记录
        GlWithdraw withdraw = new GlWithdraw();

        withdraw.setOrderId(orderId);
        withdraw.setUserId(userDO.getId());
        withdraw.setUserType(userDO.getUserType());
        withdraw.setUsername(userDO.getUsername());

        withdraw.setAmount(withdrawDO.getAmount());
        withdraw.setFee(withdrawDO.getFee());

        //银行卡提现
        if (null != userCard) {
            withdraw.setBankId(userCard.getBankId());
            withdraw.setBankName(userCard.getBankName());
            withdraw.setName(userCard.getName().trim());
            withdraw.setCardNo(userCard.getCardNo());
            withdraw.setAddress(userCard.getAddress());
        }
        //USDT提现
        if (null != usdtAddress) {
            withdraw.setBankId(FundConstant.PaymentType.DIGITAL_PAY);
            withdraw.setBankName("USDT提现");
            withdraw.setName(usdtAddress.getNickName());
            withdraw.setCardNo(usdtAddress.getProtocol());
            withdraw.setAddress(usdtAddress.getAddress());
        }

        withdraw.setClientType(withdrawDO.getClientType());
        withdraw.setCreateDate(now);
        withdraw.setLastUpdate(now);

        withdraw.setUserLevel(level.getLevelId().toString());
        withdraw.setFreeStatus(withdrawDO.getFreeStatus());
        withdraw.setAisleType(withdrawDO.getAisleType());
        withdraw.setSplitStatus(withdrawDO.getSplitStatus());
        withdraw.setIp(withdrawDO.getIp());
        withdraw.setBatchNumber(withdrawDO.getBatchNumber());//拆单批次号
        withdraw.setCoin(withdrawDO.getCoin());


        List<Integer> riskType = getRiskType(withdrawDO,userDO ,now);

        if (riskType.size() == 0) {
            riskType.add(ProjectConstant.WithdrawRiskType.NORMAL);
            withdraw.setStatus(FundConstant.WithdrawStatus.PENDING);//风险审核通过/待出款
        } else {
            withdraw.setStatus(FundConstant.WithdrawStatus.RISK_PENDING);// 风险待审核
        }
        withdraw.setRiskType(JSON.toJSONString(riskType));

        /**
         * 5096 - 提现分单(通过风控)
         */
        //出款方式默认为全部
        withdraw.setWithdrawType(FundConstant.WithdrawType.All);
        if (withdraw.getStatus() == FundConstant.WithdrawStatus.PENDING) {
            withdraw.setSeperateDate(now);
            withdraw.setSeperateCreator("系统自动");
            if (null != userCard) {
                // 提现银行卡姓名(true纯中文、false含非中文字符)
                if (RegexValidator.isChinese(userCard.getName())) {
                    withdraw = setWithdrawManual(withdraw, level.getLevelId().toString());
                } else {
                    withdraw.setSeperateReason("非纯中文姓名");
                }
            } else {
                withdraw = setWithdrawManual(withdraw, level.getLevelId().toString());
            }

        }
        return withdraw;
    }

    //提现风控
    public List<Integer> getRiskType(GlWithdrawDO withdrawDO, GlUserDO userDO, Date now) throws GlobalException {
        List<Integer> riskType = new ArrayList<>();

        String ip = withdrawDO.getIp();
        String deviceId = withdrawDO.getDeviceId();
        //大额提现直接触发大额风险类型
        if (withdrawDO.getAisleType() != null && withdrawDO.getAisleType() == 2) {
            riskType.add(ProjectConstant.WithdrawRiskType.LARGE_AMOUNT_RISK);
        }
        //游戏钱包负数风险类型
        RPCResponse<Boolean> checkGameBalance = gameUserService.checkNegative(userDO.getId());
        if (RPCResponseUtils.isSuccess(checkGameBalance) && checkGameBalance.getData()) {
            riskType.add(ProjectConstant.WithdrawRiskType.USER_BALANCE_RISK);
        }
        // 会员提现风控规则
        GlWithdrawPolicyConfig config = glWithdrawLevelConfigBusiness.getWithdrawPolicyConfig(userDO.getId(), withdrawDO.getCoin());
        // 用户提现风控数据
        RiskWithdrawResult riskData = null;
        if (config != null) {
            ip = config.getSameIpCheck() == 1 ? ip : null;
            deviceId = config.getSameDeviceCheck() == 1 ? deviceId : null;
            Integer time = null;
            if (ip != null || deviceId != null) {
                time = config.getTime();
            }
            RiskWithdrawInfoParamDO params = new RiskWithdrawInfoParamDO();
            params.setUid(userDO.getId());
            params.setIp(ip);
            params.setDeviceId(deviceId);
            params.setHours(time);
            RPCResponse<RiskWithdrawResult> response = withdrawService.withdrawInfo(params);
            if (RPCResponseUtils.isFail(response)) {
                throw new GlobalException(response.getMessage());
            }
            riskData = response.getData();
        }

        List<GlWithdrawPolicyAmountConfig> list = config.getList();
        if (null != config && riskData != null && !CollectionUtils.isEmpty(list)) {
            GlWithdrawPolicyAmountConfig amountConfig = config.getList().get(0);
            if (amountConfig.getFirstAmount() > 0 && withdrawDO.getTotalAmount().compareTo(BigDecimal.valueOf(amountConfig.getFirstAmount())) == 1) {
                //查询用户当天是否有提现命中 当日首提金额过大 风险
                int count = glWithdrawMapper.selectCountByFirstAmountRisk(userDO.getId(), DateUtils.getStartOfDay(now));
                if (count == 0) {
                    riskType.add(ProjectConstant.WithdrawRiskType.FIRST_AMOUNT_RISK);
                }
            }
            if (amountConfig.getAmount() > 0 && withdrawDO.getTotalAmount().compareTo(BigDecimal.valueOf(amountConfig.getAmount())) != -1) {
                riskType.add(ProjectConstant.WithdrawRiskType.SINGLE_AMOUNT_RISK);
            }
            if (amountConfig.getDailyTimes() > 0 && riskData.getTodayCount() >= amountConfig.getDailyTimes()) {
                riskType.add(ProjectConstant.WithdrawRiskType.DAILY_TIMES_RISK);
            }
            if (amountConfig.getDailyAmount() > 0 && riskData.getTodayAmount().add(withdrawDO.getTotalAmount()).compareTo(BigDecimal.valueOf(amountConfig.getDailyAmount())) == 1) {
                riskType.add(ProjectConstant.WithdrawRiskType.DAILY_AMOUNT_RISK);
            }
            if (amountConfig.getDailyProfit() > 0 && riskData.getTodayWin().compareTo(BigDecimal.valueOf(amountConfig.getDailyProfit())) == 1) {
                riskType.add(ProjectConstant.WithdrawRiskType.DAILY_PROFIT_RISK);
            }
            if (amountConfig.getWeeklyAmount() > 0 && riskData.getDay7Amount().add(withdrawDO.getTotalAmount()).compareTo(BigDecimal.valueOf(amountConfig.getWeeklyAmount())) == 1) {
                riskType.add(ProjectConstant.WithdrawRiskType.WEEKLY_AMOUNT_RISK);
            }
            if (config.getSameIpCheck() == 1 && riskData.getIpConflict().intValue() == 1) {
                riskType.add(ProjectConstant.WithdrawRiskType.IP_CONFLICT_RISK);
            }
            if (config.getSameDeviceCheck() == 1 && riskData.getDeviceConflict().intValue() == 1) {
                riskType.add(ProjectConstant.WithdrawRiskType.DEVICE_CONFLICT_RISK);
            }
            if (config.getRegisterDays() > 0 && DateUtils.diffDay(userDO.getRegisterDate(),now) <= config.getRegisterDays()) {
                riskType.add(ProjectConstant.WithdrawRiskType.NEW_USER_RISK);
            }
            if (amountConfig.getFirstWithdrawAmount() > 0 && withdrawDO.getTotalAmount().compareTo(BigDecimal.valueOf(amountConfig.getFirstWithdrawAmount())) == 1) {
                Integer count = glWithdrawMapper.countSuccessWithdrawByUser(userDO.getId(), userDO.getRegisterDate());
                //针对用户终身第一次提现
                if (count <= 0) {
                    riskType.add(ProjectConstant.WithdrawRiskType.FIRST_WITHDRAW_RISK);
                }
            }
        }
        //数据监控-转帐异常有待处理 提现进入风控
        GlTransferSearchParamDo paramDo = new GlTransferSearchParamDo();
        paramDo.setStartTime(userDO.getRegisterDate());
        paramDo.setEndTime(now);
        paramDo.setUserName(userDO.getUsername());
        RPCResponse<Integer> countResponse = gameTransferService.getMonitorTransferCount(paramDo);
        if (RPCResponseUtils.isFail(countResponse)) {
            throw new GlobalException(countResponse.getMessage());
        }
        if (!ObjectUtils.isEmpty(countResponse.getData()) && countResponse.getData() > 0) {
            riskType.add(ProjectConstant.WithdrawRiskType.TRANSFER_MONITOR_RISK);
        }
        return riskType;
    }

    /**
     * 根据提现设置: 自动出款 > 系统自动分单
     *
     * @param withdraw
     * @param levelId
     * @return
     * @throws GlobalException
     */
    public GlWithdraw setWithdrawManual(GlWithdraw withdraw, String levelId) {
        /**
         * 自动出款条件
         */
        GlWithdrawAutoCondition glWithdrawAutoCondition = glWithdrawAutoConditionBusiness.findAutoCondition(levelId, withdraw.getAmount());
        if (glWithdrawAutoCondition != null) {
            GlWithdrawMerchantAccount merchantAccount = this.getWithdrawMerchantAccount(glWithdrawAutoCondition, withdraw);
            withdraw.setWithdrawType(FundConstant.WithdrawType.API);
            withdraw.setSeperateReason("自动出款");

            if (merchantAccount != null) {
                //并且通过风控
                if (withdraw.getStatus() == FundConstant.WithdrawStatus.PENDING) {
                    withdraw.setMerchantId(merchantAccount.getMerchantId());
                    if (!withdraw.getCoin().equals(DigitalCoinEnum.CNY.getCode())) {
                        withdraw.setStatus(FundConstant.WithdrawStatus.AUTO_PENDING);
                        withdraw.setMerchant(merchantAccount.getChannelName());
                        withdraw.setMerchantCode(merchantAccount.getMerchantCode());
                    }
                }
            } else {
                if (withdraw.getStatus() == FundConstant.WithdrawStatus.PENDING) {
                    //无法通过第三方出款，但通过风控，修改状态为自动出款失败，人工待处理
                    withdraw.setStatus(FundConstant.WithdrawStatus.AUTO_FAILED);
                    withdraw.setRemark("无可用三方出款商户");
                }
            }
        } else {
            //1、根据用户层级、金额获取分单条件(有效)
            GlWithdrawCondition glWithdrawCondition = glWithdrawConditionBusiness.findWithdrawCondition(levelId, withdraw.getAmount());
            if (glWithdrawCondition != null) {
                withdraw.setWithdrawType(glWithdrawCondition.getWithdrawType());
            } else {
                withdraw.setSeperateReason("无匹配条件");
            }
        }
        return withdraw;
    }


    public void sendWithdrawMsg(GlWithdraw withdraw) throws GlobalException {
        if (withdraw.getStatus() == FundConstant.WithdrawStatus.PENDING && withdraw.getMerchantId() != null) {
            // 发送出款通知
            WithdrawMessage message = new WithdrawMessage();
            message.setTradeId(withdraw.getOrderId());
            message.setMerchantId(withdraw.getMerchantId());
            try {
                withdrawSender.sendWithdrawMsg(message);
            } catch (Exception e) {
                log.error(String.format("订单号：{}", withdraw.getOrderId()), e);
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
        }

    }

    public GlWithdrawMerchantAccount getWithdrawMerchantAccount(GlWithdrawAutoCondition condition, GlWithdraw withdraw) {
        List<String> merchantIds = Arrays.asList(condition.getMerchantId().split(","));
        Integer validStatus = 0;
        NameTypeEnum nameTypeEnum =  glFundBusiness.getNameType(withdraw.getName());
        List<GlWithdrawMerchantAccount> accountList = glWithdrawMerchantAccountBusiness.findMerchantList(merchantIds, validStatus, withdraw.getAmount().subtract(withdraw.getFee()), withdraw.getBankId(),nameTypeEnum.getType());
        if (accountList == null) {
            return null;
        }
        Map<Integer, BigDecimal> balanceMap = new ConcurrentHashMap<>(accountList.size());
        CompletableFuture.allOf(accountList.stream()
                .filter(a -> a.getDailyLimit() == 0 || a.getDailyLimit() >= withdraw.getAmount().longValue() + getMerchantSuccess(a.getMerchantId()))
                .map(a -> CompletableFuture.supplyAsync(() -> queryAccountBalance(a.getMerchantId()), executorService)
                        .thenAcceptAsync(b -> balanceMap.put(a.getMerchantId(), b.setScale(2, RoundingMode.DOWN)), executorService))
                .toArray(CompletableFuture[]::new))
                .join();

        if (balanceMap.size() > 0) {
            List<WithdrawAutoConditionMerchantAccount> merchantAccountList = conditionMerchantAccountBusiness.findByCondition(condition);
            Map<Integer, WithdrawAutoConditionMerchantAccount> accountMap = merchantAccountList.stream()
                    .collect(Collectors.toMap(WithdrawAutoConditionMerchantAccount::getMerchantId, a -> a));
            List<GlWithdrawMerchantAccount> accounts = accountList.stream()
                    .filter(a -> balanceMap.containsKey(a.getMerchantId()))
                    .filter(a -> !ObjectUtils.isEmpty(balanceMap.get(a.getMerchantId())))
                    .filter(a -> balanceMap.get(a.getMerchantId()).compareTo(withdraw.getAmount()) > -1) // 余额大于等于出款金额
                    .filter(a -> { // 余额大于等于设置的最低出款限额
                        WithdrawAutoConditionMerchantAccount ma = accountMap.get(a.getMerchantId());
                        BigDecimal limitAmount = ObjectUtils.isEmpty(ma) ? BigDecimal.ZERO :
                                Optional.ofNullable(ma.getLimitAmount()).orElse(BigDecimal.ZERO);
                        return balanceMap.get(a.getMerchantId()).compareTo(limitAmount) > -1;
                    }).collect(Collectors.toList());
            //极速提现
            if (withdraw.getAisleType() == 4) {
                accounts = accounts.stream().filter(obj -> obj.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)).collect(Collectors.toList());
            } else {
                accounts = accounts.stream().filter(obj -> !obj.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)).collect(Collectors.toList());
            }

            //数字币提现
            if (withdraw.getCoin().equals(DigitalCoinEnum.CNY.getCode())) {
                accounts = accounts.stream().filter(obj -> !obj.getChannelId().equals(FundConstant.PaymentChannel.DigitalPay)).collect(Collectors.toList());
            } else {
                accounts = accounts.stream().filter(obj -> obj.getChannelId().equals(FundConstant.PaymentChannel.DigitalPay)).collect(Collectors.toList());
            }

            if (!CollectionUtils.isEmpty(accounts)) {
                // 随机一个三方出款
                return accounts.get(RandomUtils.nextInt(0, accounts.size()));
            }
        }
        return null;
    }

    private long getMerchantSuccess(Integer merchantId) {
        Date now = new Date();
        String day = DateUtils.format(now, DateUtils.YYYYMMDD);
        Long success = redisService.get(RedisKeyHelper.WITHDRAW_SUCCESS_AMOUNT + day + "_" + merchantId, Long.class);
        return success == null ? 0 : success;
    }

    /**
     * 查询出款商户余额
     *
     * @param merchantId
     * @return
     * @throws GlobalException
     */
    public BigDecimal queryAccountBalance(Integer merchantId) {
        String key = String.format(RedisKeyHelper.WITHDRAW_MERCHANT_BANLANCE, merchantId);
        BigDecimal balance = redisService.get(key, BigDecimal.class);
        if (null != balance) {
            return balance;
        }

        GlWithdrawMerchantAccount merchant = glWithdrawMerchantAccountBusiness.findById(merchantId);
        if (null == merchant) {
            log.info("出款商户余额 merchant 查询异常:{}", merchantId);
            return BigDecimal.ZERO;
        }
        try {
            GlPaymentWithdrawHandler handler = glWithdrawHandlerManager.getPaymentWithdrawHandler(merchant);
            if (handler == null) {
                log.info("出款商户余额 handler 查询异常:{}", merchant.getChannelName());
                return BigDecimal.ZERO;
            }
            balance = handler.queryBalance(merchant);
            if (null == balance) {
                return BigDecimal.ZERO;
            }
            redisService.set(key, balance, 30);
            return balance;
        } catch (Exception e) {
            log.error("出款商户余额查询异常", e);
            return BigDecimal.ZERO;
        }
    }


    /**
     * 获取用户提现总金额
     *
     * @param userId
     * @param startDate
     * @param endDate
     * @return
     */
    public BigDecimal getWithdrawAmountTotal(Integer userId, Date startDate, Date endDate) {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", userId);
        param.put("startDate", startDate);
        param.put("endDate", endDate);
        param.put("status", Arrays.asList(FundConstant.WithdrawStatus.SUCCESS, FundConstant.WithdrawStatus.FORCE_SUCCESS,
                FundConstant.WithdrawStatus.RETURN_PART));
        BigDecimal amount = glWithdrawMapper.getWithdrawAmountTotal(param);

        //提现部分退回
        param.put("withdrawStatus", FundConstant.WithdrawStatus.RETURN_PART);
        param.put("status", 1);
        BigDecimal returnAmount = returnRequestMapper.getAmountTotal(param);

        return amount == null ? BigDecimal.ZERO : amount.subtract(returnAmount);
    }

    /**
     * 获取最后一笔提现记录
     *
     * @param userId
     * @return
     * @throws GlobalException
     */
    public GlWithdraw getLastWithdraw(Integer userId) {
        return glWithdrawMapper.getLastWithdraw(userId);
    }

    /**
     * 是否有未结束提现订单
     *
     * @param userId
     * @return
     * @throws GlobalException
     */
    public boolean validateLastWithdraw(Integer userId) {
        GlWithdraw lastWithdraw = getLastWithdraw(userId);
        if (lastWithdraw != null) {
            if (lastWithdraw.getStatus() == FundConstant.WithdrawStatus.REVIEW_HOLD
                    ||lastWithdraw.getStatus() == FundConstant.WithdrawStatus.RISK_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.SUCCESS_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_FAILED
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.WITHDRAW_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.RECHARGE_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING
                    || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.CONFIRM_PENDING) {
                return true;
            }
            GlWithdrawSplit withdrawSplit = glWithdrawSplitMapper.selectByPrimaryKey(lastWithdraw.getOrderId());
            if (withdrawSplit != null) {
                GlWithdrawSplit condition = new GlWithdrawSplit();
                condition.setParentId(withdrawSplit.getParentId());
                List<GlWithdrawSplit> withdrawSplitList = glWithdrawSplitMapper.select(condition);
                for (GlWithdrawSplit split : withdrawSplitList) {
                    GlWithdraw splitWithdraw = findById(split.getOrderId());
                    if (splitWithdraw.getStatus() == FundConstant.WithdrawStatus.REVIEW_HOLD
                            ||splitWithdraw.getStatus() == FundConstant.WithdrawStatus.RISK_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.SUCCESS_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_FAILED
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                            || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.WITHDRAW_PENDING
                            || lastWithdraw.getStatus() == FundConstant.WithdrawStatus.RECHARGE_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING
                            || splitWithdraw.getStatus() == FundConstant.WithdrawStatus.CONFIRM_PENDING) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getWithdrawCount(Integer userId, Date startDate, Date endDate, Integer freeStatus, Integer aisleType, String coin) {
        Condition condition = new Condition(GlWithdraw.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn("status", Arrays.asList(FundConstant.WithdrawStatus.SUCCESS,
                FundConstant.WithdrawStatus.FORCE_SUCCESS, FundConstant.WithdrawStatus.RETURN_PART));
        if (null != userId) {
            criteria.andEqualTo("userId", userId);
        }
        if (null != startDate) {
            criteria.andGreaterThanOrEqualTo("createDate", startDate);
        }
        if (null != endDate) {
            criteria.andLessThanOrEqualTo("createDate", endDate);
        }
        if (null != freeStatus) {
            criteria.andEqualTo("freeStatus", freeStatus);
        }
        if (null != aisleType) {
            criteria.andEqualTo("aisleType", aisleType);
        }
        if (StringUtils.isNotEmpty(coin)) {
            criteria.andEqualTo("coin", coin);
        }
        List<GlWithdraw> glWithdrawList = glWithdrawMapper.selectByCondition(condition);
        int count = 0;
        Map<String, String> parentId = new HashMap<>();
        if (glWithdrawList != null && glWithdrawList.size() != 0) {
            for (GlWithdraw glWithdraw : glWithdrawList) {
                GlWithdrawSplit split = glWithdrawSplitMapper.selectByPrimaryKey(glWithdraw.getOrderId());
                if (split != null) { //拆单
                    if (!parentId.containsKey(split.getParentId())) {
                        parentId.put(split.getParentId(), split.getParentId());
                        count++;
                    }
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 自动出款失败
     *
     * @param merchantAccount
     * @param remark
     * @param approve
     * @throws GlobalException
     */
    public void doWithdrawApiFail(String orderId, GlWithdrawMerchantAccount merchantAccount, String remark, String approve) throws GlobalException {
        GlWithdraw withdraw = glWithdrawMapper.selectForUpdate(orderId);
        Date now = new Date();
        if (merchantAccount != null) {
            withdraw.setApprover(approve);
            withdraw.setApproveTime(now);
            withdraw.setLastUpdate(now);
            withdraw.setMerchant(merchantAccount.getChannelName());
            withdraw.setMerchantCode(merchantAccount.getMerchantCode());
            withdraw.setMerchantId(merchantAccount.getMerchantId());
        }
        withdraw.setStatus(FundConstant.WithdrawStatus.AUTO_FAILED);
        withdraw.setRemark(remark);
        withdraw.setWithdrawType(withdraw.getWithdrawType());
        glWithdrawMapper.updateWithdraw(withdraw);
    }


    /**
     * 修改提现订单备注信息
     */
    public void updateWithdrawRemark(WithdrawRequestDO requestDO) throws GlobalException {
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(requestDO.getOrderId());
        if (null == glWithdraw) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        glWithdrawMapper.updateRemark(requestDO.getOrderId(), requestDO.getRemark());
    }


    /**
     * 三方手动出款和人工出款
     *
     * @param dto
     * @param adminDO
     */
    public void approveWithdraw(WithdrawApproveDO dto, GlAdminDO adminDO) throws GlobalException {
        //避免同时处理出款订单
        String key = RedisKeyHelper.WITHDRAW_PENDING_LIST_NEW + "_" + dto.getOrderId();
        if (redisService.exists(key)) {
            String lastOperator = redisService.get(key);
            throw new GlobalException(ResultCode.DATA_ERROR, "订单处理中,处理人:" + lastOperator);
        }
        redisService.set(key, adminDO.getUsername(), 180);

        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(dto.getOrderId());

        if (null == dbWithdraw || (0 != dbWithdraw.getStatus() && 7 != dbWithdraw.getStatus())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现记录不存在或不可操作");
        }

        GlWithdrawApprove dbApv = withdrawApproveBusiness.findById(dto.getOrderId());
        if (dbApv != null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现已审核");
        }
        boolean checkExist = withdrawReturnRequestBusiness.checkExistApproveRecord(dto.getOrderId());
        if (checkExist && 2 == dto.getStatus()) {
            throw new GlobalException(ResultCode.DATA_ERROR, "当前订单已走过完整流程，不能重新被审核");
        }

        dbWithdraw.setWithdrawType(dto.getWithdrawType());

        // 三方手动出款
        if (dbWithdraw.getWithdrawType() == FundConstant.WithdrawType.API_MANUAL) {
            if (dto.getStatus() == 1) { //通过出款
                if (dto.getMerchantId() == null || StringUtils.isEmpty(dto.getRemark())) {
                    throw new GlobalException(ResultCode.DATA_ERROR);
                }
                GlWithdrawMerchantAccount merchantAccount = glWithdrawMerchantAccountBusiness.getWithdrawMerchant(dto.getMerchantId());
                if (merchantAccount == null || merchantAccount.getStatus() != 0) {
                    throw new GlobalException(ResultCode.DATA_ERROR);
                }
                if (dbWithdraw.getAisleType() == FundConstant.AisleType.C2C && !merchantAccount.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)) {
                    throw new GlobalException("C2C支付只支持极速提现，请选择其他商户出款");
                }
                //调用三方支付
                glWithdrawTransactionalBusiness.doWithdrawApi(dbWithdraw, merchantAccount, adminDO.getUsername(), dto.getRemark());
            } else {
                //拒绝出款
                glWithdrawTransactionalBusiness.doWithdrawApiRefuse(dbWithdraw, dto, adminDO);
            }
        } else if (dbWithdraw.getWithdrawType() == FundConstant.WithdrawType.MANUAL) {
            // 人工出款
            if (1 == dto.getStatus()
                    && StringUtils.isBlank(dto.getRemark())
                    && StringUtils.isBlank(dto.getTransferName())
                    && StringUtils.isBlank(dto.getTransferBankName())) {
                throw new GlobalException(ResultCode.DATA_ERROR);
            }
            glWithdrawTransactionalBusiness.doWithdrawApprove(dbWithdraw, dto, adminDO);
        }
        //处理过的出款订单
        redisService.delete(key);
    }

    public void manualWithdrawReturn(String orderId) throws GlobalException {
        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (null == dbWithdraw
                || dbWithdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_PENDING
                || !dbWithdraw.getMerchant().equals("StormPay")) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现记录不存在或不可操作");
        }

        GlAdminDO adminDO = new GlAdminDO();
        adminDO.setUserId(437);
        adminDO.setUsername("darren");


        WithdrawApproveDO dto = new WithdrawApproveDO();
        dto.setOrderId(dbWithdraw.getOrderId());
        dto.setRemark("临时任务处理，批量退回风云聚合提现订单");
        dto.setRejectReason("出款系统异常");
        dto.setStatus(2);
        dto.setWithdrawType(0);
        glWithdrawTransactionalBusiness.doWithdrawApprove(dbWithdraw, dto, adminDO);
    }

    /**
     * 编辑出款标签
     *
     * @param orderId
     * @param tag
     * @throws GlobalException
     */
    public void updateTag(String orderId, String tag) throws GlobalException {
        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (ObjectUtils.isEmpty(dbWithdraw)) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        dbWithdraw.setTag(tag);
        glWithdrawMapper.updateByPrimaryKey(dbWithdraw);
    }

    /**
     * 申请提现退回
     *
     * @param requestDO
     * @param adminDO
     */
    public void requestWithdrawReturn(WithdrawRequestDO requestDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(requestDO.getOrderId());
        if (null == dbWithdraw) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        GlWithdrawReturnRequest returnRequest = glWithdrawReturnRequestMapper.selectByPrimaryKey(dbWithdraw.getOrderId());
        if (null != returnRequest) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        glWithdrawTransactionalBusiness.doReturnRequest(requestDO, adminDO);
    }

    /**
     * 申请强制成功
     *
     * @param requestDO
     * @param adminDO
     */
    public void requestWithdrawSuccess(WithdrawRequestDO requestDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(requestDO.getOrderId());
        if (null == dbWithdraw) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现订单不存在");
        }

        GlWithdrawReturnRequest returnRequest = glWithdrawReturnRequestMapper.selectByPrimaryKey(dbWithdraw.getOrderId());
        if (null != returnRequest) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现订单已申请审核，禁止重复操作");
        }

        glWithdrawTransactionalBusiness.doSuccessRequest(requestDO, adminDO);
    }

    /**
     * 查询提现退回申请人列表
     *
     * @return
     */
    public List<String> getAllReturnCreator() {
        String listCreator = redisService.get(RedisKeyHelper.WITHDRAW_RETURN_CREATOR);
        if (listCreator != null) {
            return JSONArray.parseArray(listCreator, String.class);
        }
        List<String> result = glWithdrawReturnRequestMapper.findAllCreator();
        redisService.set(RedisKeyHelper.WITHDRAW_RETURN_CREATOR, JSON.toJSONString(result), 3600);
        return result;
    }

    /**
     * 查询提现退回审核人
     *
     * @return
     */
    public List<String> getAllReturnApprover() {
        String listCreator = redisService.get(RedisKeyHelper.WITHDRAW_RETURN_APPROVE);
        if (listCreator != null) {
            return JSONArray.parseArray(listCreator, String.class);
        }
        List<String> result = glWithdrawReturnRequestMapper.findAllApprover();
        redisService.set(RedisKeyHelper.WITHDRAW_RETURN_APPROVE, JSON.toJSONString(result), 3600);
        return result;
    }

    /**
     * 审核提现退回、提现成功
     *
     * @param approveDO
     * @param adminDO
     */
    public void doWithdrawRequestApprove(WithdrawRequestApproveDO approveDO, GlAdminDO adminDO) throws GlobalException {

        GlWithdrawReturnRequest dbReq = glWithdrawReturnRequestMapper.selectByPrimaryKey(approveDO.getOrderId());
        if (null == dbReq || 0 != dbReq.getStatus()) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        if (dbReq.getType() == 0) { //提现退回
            glWithdrawTransactionalBusiness.doWithdrawReturnApprove(approveDO, adminDO);
        } else if (dbReq.getType() == 1) { //强制成功
            glWithdrawTransactionalBusiness.doWithdrawForceSuccess(approveDO, adminDO);
        }
    }

    public List<GlWithdrawAlarm> withdrawAlarms(WithdrawAlarmDto alarmDto) throws GlobalException {

        //设置接口同一ip 30S 内只能请求一次
        if (!ObjectUtils.isEmpty(redisService.get("WITHDRAW_ALARM_IP_LIMIT" + alarmDto.getIp()))) {
            throw new GlobalException("访问频率超限，请稍后再试");
        }

        redisService.set("WITHDRAW_ALARM_IP_LIMIT" + alarmDto.getIp(), "1", 30);
        String key = "WHwyCvmZf1zkPjwkYQJedQFgpdTojbIh";
        String verifySign = MD5.md5(key + alarmDto.getTimeStamp());

        if (!verifySign.equals(alarmDto.getSign())) {
            throw new GlobalException("签名验证失败");
        }
        Date now = new Date();
        Date startTime = null;
        try {
            startTime = DateUtils.addMin(-3, now);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        List<GlWithdrawAlarm> result = withdrawAlarmBusiness.getAlarmList(startTime, now);
        return result;
    }

    /**
     * 批量查询
     *
     * @param orderIds
     * @return
     */
    public List<GlWithdraw> findByOrderIds(List<String> orderIds) {
        if (CollectionUtils.isEmpty(orderIds)) {
            return Lists.newArrayList();
        }
        orderIds = orderIds.stream().distinct().collect(Collectors.toList());
        return findByIds(String.format("'%s'", StringUtils.join(orderIds, "','")));
    }

    public WithdrawNotify artificialDoWithdrawQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        try {
            GlPaymentWithdrawHandler handler = glWithdrawHandlerManager.getPaymentWithdrawHandler(merchant);
            if (handler == null) {
                log.error("no_handler_for_withdraw_channel {}.", merchant.getChannelId());
                return null;
            }
            WithdrawNotify notify = handler.doTransferQuery(merchant, orderId);
            if (notify == null) {
                return null;
            }
            return notify;
        } catch (Exception e) {
            log.error("do_artificialDoWithdrawNotify_error", e);
            throw new GlobalException(ResultCode.DATA_ERROR, "三方查询异常，请稍后再试");
        }
    }

    public BigDecimal getWithdrawingTotal(Integer userId) {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", userId);
        param.put("startDate", null);
        param.put("endDate", null);
        param.put("status", Arrays.asList(FundConstant.WithdrawStatus.RISK_PENDING, FundConstant.WithdrawStatus.PENDING,
                FundConstant.WithdrawStatus.SUCCESS_PENDING, FundConstant.WithdrawStatus.AUTO_FAILED,
                FundConstant.WithdrawStatus.REVIEW_HOLD, FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT,
                FundConstant.WithdrawStatus.CONFIRM_PENDING));
        BigDecimal amount = glWithdrawMapper.getWithdrawAmountTotal(param);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public List<WithdrawBankSettingResult> getBankSettingInfo() throws GlobalException {
        RedisResult<WithdrawBankSettingResult> redisResult = redisService.getListResult(RedisKeyHelper.WITHDRAW_BANK_SETTING_CACHE, WithdrawBankSettingResult.class);
        if (redisResult.isExist()) {
            return redisResult.getListResult();
        }

        List<GlWithdrawBank> bankList = glWithdrawBankBusiness.findAll();

        List<WithdrawBankSettingResult> results = new ArrayList<>();
        for (GlWithdrawBank bank : bankList) {
            WithdrawBankSettingResult bankSettingResult = new WithdrawBankSettingResult();
            bankSettingResult.setBankId(bank.getBankId());
            bankSettingResult.setBankName(bank.getBankName());
            bankSettingResult.setStatus(1);
            results.add(bankSettingResult);
        }
        redisService.set(RedisKeyHelper.WITHDRAW_BANK_SETTING_CACHE, results, -1);
        return results;
    }

    public void saveBankSetting(List<Integer> bankIds) throws GlobalException {
        List<GlWithdrawBank> bankList = glWithdrawBankBusiness.findAll();

        List<WithdrawBankSettingResult> results = new ArrayList<>();
        for (GlWithdrawBank bank : bankList) {
            WithdrawBankSettingResult bankSettingResult = new WithdrawBankSettingResult();
            bankSettingResult.setBankId(bank.getBankId());
            bankSettingResult.setBankName(bank.getBankName());
            if (!ObjectUtils.isEmpty(bankIds) && bankIds.contains(bank.getBankId())) {
                bankSettingResult.setStatus(0);
            } else {
                bankSettingResult.setStatus(1);
            }
            results.add(bankSettingResult);
        }
        redisService.delete(RedisKeyHelper.WITHDRAW_BANK_SETTING_CACHE);
        redisService.set(RedisKeyHelper.WITHDRAW_BANK_SETTING_CACHE, results, -1);
    }

    /**
     * 查询提现订单详情
     *
     * @param orderId
     * @return
     */
    public GlWithdrawResult getWithdrawDetali(String orderId) {
        GlWithdraw withdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (null == withdraw) {
            return null;
        }
        GlWithdrawResult result = new GlWithdrawResult();
        GlWithdrawDO withdrawDO = DtoUtils.transformBean(withdraw, GlWithdrawDO.class);
        result.setWithdrawDO(withdrawDO);

        GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoMapper.selectByPrimaryKey(withdraw.getOrderId());
        if (null != receiveInfo) {
            result.setWithdrawReceiveInfo(receiveInfo);
        }
        return result;
    }


    public BigDecimal getWithdrawRate(Integer userType) {
        BigDecimal rate = BigDecimal.ZERO;
        log.info("getWithdrawRate userType:{}" ,userType ) ;
        if (UserConstant.UserType.PLAYER == userType) {
            rate = redisService.get(RedisKeyHelper.WITHDRAW_OKEX_USDT_RATE, BigDecimal.class);
        } else {
            rate = redisService.get(RedisKeyHelper.WITHDRAW_OKEX_USDT_RATE, BigDecimal.class);
            if (!ObjectUtils.isEmpty(rate)) {
                rate = rate.add(BigDecimal.valueOf(0.3));
            }
        }
        log.info("getWithdrawRate rate:{}" ,rate ) ;
        return rate;
    }

    /**
     * 同步三方提现订单状态
     * @throws GlobalException
     */
    public void syncWithdraw(String channelIdStr) throws GlobalException {

        List<String> channelIds = Lists.newArrayList();
        if (StringUtils.isNotEmpty(channelIdStr)) {
            channelIds = Arrays.asList(channelIdStr.split(","));
        }

        Date now = new Date();
        GlWithdrawQueryDto queryDto = new GlWithdrawQueryDto();
        queryDto.setPage(1);
        queryDto.setSize(100);
        queryDto.setStartTime(DateUtils.getMinTime(DateUtils.addDay(-1, now)));
        queryDto.setEndTime(now);
        queryDto.setWithdrawStatus(Arrays.asList(FundConstant.WithdrawStatus.AUTO_PENDING));
        GlWithdrawCollectResult<WithdrawVO> page = getWithdrawHistoryPageList(queryDto);

        int pages = page.getPages();
        List<WithdrawVO> records = page.getList();
        StringBuffer data = new StringBuffer();
        for (int i = 1; i <= pages; i++) {
            if (i > 1) {
                queryDto.setPage(i + 1);
                page = getWithdrawHistoryPageList(queryDto);
                records = page.getList();
            }
            for (WithdrawVO record : records) {
                notify(record, channelIds);
            }
        }
    }

    public WithdrawNotify notify(WithdrawVO record, List<String> channelIds) throws GlobalException {
        WithdrawNotify withdrawNotify = null;
        try {
            log.info("withdraw_notify: {}", JSON.toJSONString(record));
            GlWithdrawMerchantAccount merchant = glWithdrawMerchantAccountBusiness.findById(record.getMerchantId());
            if (merchant == null) {
                log.error("回调失败 订单号:{}", JSON.toJSONString(record));
            }
            //过滤支持回调的渠道
            if (!CollectionUtils.isEmpty(channelIds) && !channelIds.contains(merchant.getChannelId().toString())) {
                return null;
            }
            Map<String, String> resMap = new HashMap<>();
            resMap.put("orderId",record.getOrderId());
            resMap.put("thirdOrderId", record.getThirdOrderId());
            resMap.put("amount", record.getAmount() + "");
            resMap.put("status", record.getStatus() + "");
            withdrawNotify = doWithdrawNotify(merchant,  resMap);
            if (withdrawNotify == null || withdrawNotify.getStatus() == 2) {
                log.warn("withdraw_notify_error: {}", null != withdrawNotify ? JSON.toJSONString(withdrawNotify) : JSON.toJSONString(resMap));
                return withdrawNotify;
            }
            // 提交到账事务
            glWithdrawTransactionalBusiness.doWithdrawNofity(withdrawNotify);
        } catch (Exception e) {
            log.error("withdrawNotify_error:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        }
        return withdrawNotify;
    }


    public GlWithdrawCollectResult<WithdrawVO> getWithdrawHistoryPageList(GlWithdrawQueryDto queryDto) {
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        queryDto.setOrderByClause("create_date");
        queryDto.setSortStr("desc");
        List<WithdrawVO> withdrawByPage = glWithdrawMapper.getWithdrawByPage(queryDto);
        return new GlWithdrawCollectResult<>(withdrawByPage);
    }
    public List<GlWithdraw> findExpired(int minutes, Integer status, Integer aisleType) {
        Condition con = new Condition(GlWithdraw.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("status", status)
                .andEqualTo("aisleType", aisleType)
                .andLessThan("lastUpdate", org.apache.commons.lang3.time.DateUtils.addMinutes(new Date(), -minutes));
        con.setOrderByClause("create_date asc");
        return glWithdrawMapper.selectByCondition(con);
    }


    /**
     * 2个月内待审核提现订单数量
     *
     * @return
     */
    public Integer approveCount() {
        return glWithdrawMapper.approveCount(DateUtils.addMonth(-2,new Date()));
    }

}
