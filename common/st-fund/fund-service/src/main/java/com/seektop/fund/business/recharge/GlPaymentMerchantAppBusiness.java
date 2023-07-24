package com.seektop.fund.business.recharge;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.Language;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.MerchantAppPage;
import com.seektop.fund.controller.backend.param.recharge.RechargeCommonAmountDO;
import com.seektop.fund.controller.backend.param.recharge.app.*;
import com.seektop.fund.controller.backend.result.GlPaymentBankResult;
import com.seektop.fund.controller.backend.result.GlPaymentMerchantResult;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.backend.result.recharge.PaymentMerchantAppDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.UseModeEnum;
import com.seektop.fund.mapper.GlPaymentMerchantAppMapper;
import com.seektop.fund.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GlPaymentMerchantAppBusiness extends AbstractBusiness<GlPaymentMerchantApp> {

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private GlPaymentMerchantFeeBusiness glPaymentMerchantFeeBusiness;
    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;
    @Resource
    private GlPaymentMerchantAppMapper glPaymentMerchantAppMapper;
    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantaccountBusiness;

    @Resource
    private RedisService redisService;
    @Resource
    private GlWithdrawConfigBusiness configBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;
    @Resource(name = "c2CPaymentIds")
    private List<Integer> c2CPaymentIds;
    @Resource
    private GlFundPaymentRecommendAmountBusiness glFundPaymentRecommendAmountBusiness;

    /**
     * 获取普通充值渠道缓存
     *
     * @param levelId
     * @param clientType
     * @return
     */
    public List<GlPaymentResult> getPaymentCache(Integer levelId, Integer clientType) {
        return redisService.getHashList(RedisKeyHelper.PAYMENT_MERCHANT_APP_NORMAL_CACHE + levelId, clientType.toString(), GlPaymentResult.class);
    }

    /**
     * 获取大额充值渠道缓存
     *
     * @param levelId
     * @param clientType
     * @return
     */
    public List<GlPaymentResult> getPaymentLargeCache(Integer levelId, Integer clientType) {
        return redisService.getHashList(RedisKeyHelper.PAYMENT_MERCHANT_APP_LARGE_CACHE + levelId, clientType.toString(), GlPaymentResult.class);
    }

    /**
     * 更新充值渠道Redis缓存
     */
    public void updatePaymentCache() {
        for (Integer limitType : FundConstant.PAYMENT_CACHE_LIST) {
            List<GlFundUserlevel> userlevelList = glFundUserlevelBusiness.findAll();
            for (GlFundUserlevel userlevel : userlevelList) {
                // 根据层级获取充值渠道
                List<GlPaymentMerchantApp> appList = getAppList(limitType, userlevel.getLevelId());

                String key = null;
                if (limitType == FundConstant.PaymentCache.NORMAL) {
                    key = RedisKeyHelper.PAYMENT_MERCHANT_APP_NORMAL_CACHE;
                } else if (limitType == FundConstant.PaymentCache.LARGE) {
                    key = RedisKeyHelper.PAYMENT_MERCHANT_APP_LARGE_CACHE;
                }
                if (ObjectUtils.isEmpty(appList)) {
                    redisService.delete(key + userlevel.getLevelId());
                    continue;
                }

                //按客户端类型区分支付渠道
                Map<Integer, Map<Integer, GlPaymentResult>> clientPaymentMap = getClientType(appList, limitType);

                updateCache(clientPaymentMap, userlevel.getLevelId(), key);
            }
        }
    }

    public GlPaymentMerchantApp selectOneByEntity(Integer paymentId, Integer merchantId, Integer userMode) {
        return glPaymentMerchantAppMapper.selectOneByEntity(paymentId, merchantId, userMode);
    }

    /**
     * 同步三方应用渠道类型、商户号
     *
     * @param merchantId
     * @param limitType
     * @param merchantCode
     */
    public void SyncLimitType(Integer merchantId, Integer limitType, String merchantCode) {
        glPaymentMerchantAppMapper.SyncLimitType(merchantId, limitType, merchantCode);
    }

    /**
     * 同步三方应用上下架状态
     *
     * @param merchantId
     * @param status
     */
    public void SyncStatus(Integer merchantId, Integer status) {
        glPaymentMerchantAppMapper.SyncStatus(merchantId, status);
    }

    /**
     * 同步三方应用商户开启状态
     *
     * @param merchantIds
     * @param openStatus
     */
    public void SyncOpenStatus(List<Integer> merchantIds, Integer openStatus) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("merchantIds", merchantIds);
        paramMap.put("openStatus", openStatus);
        glPaymentMerchantAppMapper.SyncOpenStatus(paramMap);
    }


    public List<GlPaymentMerchantApp> findList(Integer paymentId, Integer merchantId, Integer clientType, Integer useMode, Integer limitType) {
        Condition con = new Condition(GlPaymentMerchantApp.class);
        Example.Criteria criteria = con.createCriteria();
        if (!ObjectUtils.isEmpty(paymentId)) {
            criteria.andEqualTo("paymentId", paymentId);
        }
        if (!ObjectUtils.isEmpty(merchantId)) {
            criteria.andEqualTo("merchantId", merchantId);
        }
        if (!ObjectUtils.isEmpty(clientType) && clientType != -1) {
            criteria.andEqualTo("clientType", clientType);
        }
        if (!ObjectUtils.isEmpty(useMode)) {
            criteria.andEqualTo("useMode", useMode);
        }
        if (!ObjectUtils.isEmpty(limitType) && limitType != -1) {
            criteria.andEqualTo("limitType", limitType);
        }
        criteria.andNotEqualTo("status", 2);
        con.setOrderByClause("create_date asc");
        return findByCondition(con);
    }

    /**
     * 分页查询
     *
     * @param dto
     * @return
     */
    public PageInfo<GlPaymentMerchantApp> getPageList(MerchantAccountAppQueryDO dto) {
        Page<Object> page = PageHelper.startPage(dto.getPage(), dto.getSize());
        Condition con = new Condition(GlPaymentMerchantApp.class);
        Example.Criteria criteria = con.createCriteria();
        if (!ObjectUtils.isEmpty(dto.getPaymentIds())) {
            criteria.andIn("paymentId", dto.getPaymentIds());
        }
        if (!ObjectUtils.isEmpty(dto.getChannelId()) && dto.getChannelId() != -1) {
            criteria.andEqualTo("channelId", dto.getChannelId());
        }
        if (!ObjectUtils.isEmpty(dto.getMerchantCode())) {
            criteria.andEqualTo("merchantCode", dto.getMerchantCode());
        }
        if (!ObjectUtils.isEmpty(dto.getLimitType()) && dto.getLimitType() != -1) {
            criteria.andEqualTo("limitType", dto.getLimitType());
        }
        if (!ObjectUtils.isEmpty(dto.getLevelIds())) {
            boolean isFirst = true;
            StringBuilder conditionQuery = new StringBuilder();
            conditionQuery.append("(");
            for (Integer levelId : dto.getLevelIds()) {
                if (isFirst) {
                    conditionQuery.append("find_in_set('" + levelId + "',level_id)");
                    isFirst = false;
                } else {
                    conditionQuery.append("OR find_in_set('" + levelId + "',level_id)");
                }
            }
            conditionQuery.append(")");
            criteria.andCondition(conditionQuery.toString());
        }
        if (!ObjectUtils.isEmpty(dto.getStatus()) && dto.getStatus() != -1) {
            criteria.andEqualTo("status", dto.getStatus());
        } else {
            criteria.andNotEqualTo("status", 2);
        }
        if (!ObjectUtils.isEmpty(dto.getOpenStatus()) && dto.getOpenStatus() != -1) {
            criteria.andEqualTo("openStatus", dto.getOpenStatus());
        }
        if (!ObjectUtils.isEmpty(dto.getClientType()) && dto.getClientType() != -1) {
            criteria.andEqualTo("clientType", dto.getClientType());
        }
        if (!ObjectUtils.isEmpty(dto.getUseMode()) && dto.getUseMode() > -1) {
            criteria.andEqualTo("useMode", dto.getUseMode());
        }
        if (!ObjectUtils.isEmpty(dto.getCoin())) {
            criteria.andEqualTo("coin", dto.getCoin());
        }
        con.setOrderByClause("open_status asc,status asc,cycle_priority desc,cycle_count desc,last_update desc");
        List<GlPaymentMerchantApp> list = findByCondition(con);
        PageInfo<GlPaymentMerchantApp> pageInfo = new PageInfo(list);
        pageInfo.setTotal(page.getTotal());
        return pageInfo;
    }

    /**
     * 获取商户应用
     *
     * @param limitType
     * @param levelId
     * @return
     */
    private List<GlPaymentMerchantApp> getAppList(Integer limitType, Integer levelId) {
        Condition condition = new Condition(GlPaymentMerchantApp.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", ProjectConstant.CommonStatus.NORMAL);
        criteria.andEqualTo("openStatus", ProjectConstant.CommonStatus.NORMAL);
        criteria.andEqualTo("limitType", limitType);
        criteria.andIn("useMode", Lists.newArrayList(UseModeEnum.APP.getCode(),UseModeEnum.C2C.getCode())); // 极速转卡
        criteria.andCondition("find_in_set('" + levelId + "',level_id)");
        return glPaymentMerchantAppMapper.selectByCondition(condition);
    }

    private Map<Integer, Map<Integer, GlPaymentResult>> getClientType(List<GlPaymentMerchantApp> appList, Integer limitType) {
        Map<Integer, Map<Integer, GlPaymentResult>> clientPaymentMap = new HashMap<>();
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);

        List<GlFundPaymentRecommendAmount> paymentAmounts =  glFundPaymentRecommendAmountBusiness.findAll();

        for (GlPaymentMerchantApp merchantApp : appList) {
            Map<Integer, GlPaymentResult> paymentMap = clientPaymentMap.get(merchantApp.getClientType());
            if (null == paymentMap) {
                paymentMap = new HashMap<>();
            }
            GlPaymentResult paymentResult = paymentMap.get(merchantApp.getPaymentId());
            if (null == paymentResult) {
                paymentResult = new GlPaymentResult();
            }
            paymentResult.setPaymentId(merchantApp.getPaymentId());
            paymentResult.setPaymentName(merchantApp.getPaymentName());

            List<GlPaymentMerchantResult> merchantList = paymentResult.getMerchantList();
            if (merchantList == null) {
                merchantList = new ArrayList<>();
            }
            GlPaymentMerchantResult result = new GlPaymentMerchantResult();
            result.setAppId(merchantApp.getId());
            result.setMerchantId(merchantApp.getMerchantId());
            result.setMerchantName(merchantApp.getChannelName());
            result.setMerchantCode(merchantApp.getMerchantCode());
            result.setChannelId(merchantApp.getChannelId());
            result.setChannelName(merchantApp.getChannelName());
            result.setRecommendStatus(merchantApp.getRecommendStatus());
            result.setTopStatus(merchantApp.getTopStatus());
            result.setTopDate(merchantApp.getTopDate());
            if (StringUtils.isNotEmpty(merchantApp.getQuickAmount())) {
                List<String> temp = Arrays.asList(merchantApp.getQuickAmount().trim().split(","));
                List<Integer> quickAmount = new ArrayList<>();
                temp.forEach(t -> quickAmount.add(Integer.parseInt(t.trim())));
                result.setQuickAmount(quickAmount);
            }
            if (c2CPaymentIds.stream().anyMatch(id -> id.equals(merchantApp.getPaymentId())) && configDO != null) {
                    result.setQuickAmount(configDO.getChooseAmounts());
            }
            // 支付类型 - 手续费以及限额设置
            GlPaymentMerchantFee fee = glPaymentMerchantFeeBusiness.findFee(limitType, merchantApp.getMerchantId(), merchantApp.getPaymentId());
            // 未配置充值金额限额以及手续费；不展示改通道
            if (null == fee) {
                continue;
            }
            // 配置充值限额0-0  不展示通道
            if (fee.getMinAmount().compareTo(BigDecimal.ZERO) == 0 && fee.getMaxAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            result.setFee(fee.getFeeRate());
            result.setFeeLimit(fee.getMaxFee());
            result.setMinAmount(fee.getMinAmount());
            result.setMaxAmount(fee.getMaxAmount());
            // 银行卡列表  网银支付的时候初始化
            List<GlPaymentBankResult> bankList = new ArrayList<>();
            if (merchantApp.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
                List<GlPaymentChannelBank> channelBankList = glPaymentChannelBankBusiness.findList(merchantApp.getChannelId());
                for (GlPaymentChannelBank bank : channelBankList) {
                    if (bank.getStatus() == ProjectConstant.CommonStatus.NORMAL) {
                        GlPaymentBankResult bankResult = new GlPaymentBankResult();
                        bankResult.setStatus(bank.getStatus());
                        bankResult.setBankId(bank.getBankId());
                        bankResult.setBankName(bank.getBankName());
                        bankResult.setMinAmount(bank.getMinAmount());
                        bankResult.setMaxAmount(bank.getMaxAmount());
                        bankList.add(bankResult);
                    }
                }
            }
            // 银行卡转账  需要用户选择付款银行时初始化
            if (merchantApp.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
                if (merchantApp.getChannelId().equals(FundConstant.PaymentChannel.STORMPAY)
                        || merchantApp.getChannelId().equals(FundConstant.PaymentChannel.STPAYER)
                        || merchantApp.getChannelId().equals(FundConstant.PaymentChannel.LELIPAY)) {
                    List<GlPaymentChannelBank> channelBankList = glPaymentChannelBankBusiness.findList(merchantApp.getChannelId());
                    for (GlPaymentChannelBank bank : channelBankList) {
                        if (bank.getStatus() == ProjectConstant.CommonStatus.NORMAL) {
                            GlPaymentBankResult bankResult = new GlPaymentBankResult();
                            bankResult.setBankId(bank.getBankId());
                            bankResult.setBankName(bank.getBankName());
                            bankList.add(bankResult);
                        }
                    }
                }
            }
            result.setBankList(bankList);
            result.setMaxCount(merchantApp.getMaxCount());
            result.setCycleCount(merchantApp.getCycleCount());
            result.setCyclePriority(merchantApp.getCyclePriority());
            GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountBusiness.findOne(merchantApp.getMerchantId());
            if (!ObjectUtils.isEmpty(merchantaccount)) {
                result.setLimitAmount(merchantaccount.getLimitAmount());
            }
            result.setNameType(Arrays.asList(merchantApp.getNameType().split(",")));
            result.setVipLevel(Arrays.asList(merchantApp.getVipLevel().split(",")));
            paymentAmounts.stream().filter(item -> item.getPaymentId().intValue() == merchantApp.getPaymentId())
                    .filter(item -> StringUtils.isNotEmpty(item.getRecommendAmount())).forEach(obj -> {
                List<String> recommendAmounts = Arrays.asList(obj.getRecommendAmount().trim().split(","));
                result.setRecommendAmount(recommendAmounts.stream().map(Integer::parseInt).collect(Collectors.toList()));
            });
            result.setCoin(merchantApp.getCoin());
            merchantList.add(result);
            paymentResult.setMerchantList(merchantList);

            paymentMap.put(merchantApp.getPaymentId(), paymentResult);
            clientPaymentMap.put(merchantApp.getClientType(), paymentMap);
        }
        return clientPaymentMap;
    }

    private void updateCache(Map<Integer, Map<Integer, GlPaymentResult>> clientPaymentMap, Integer levelId, String key) {
        Map<Integer, GlPaymentResult> allClientPaymentMap = clientPaymentMap.get(ProjectConstant.ClientType.ALL);
        for (int clientType : ProjectConstant.CLIENT_LIST) {
            if (clientType == ProjectConstant.ClientType.ALL) {
                continue;
            }
            Map<Integer, GlPaymentResult> paymentMap = clientPaymentMap.get(clientType);
            if (ObjectUtils.isEmpty(paymentMap)) {
                paymentMap = allClientPaymentMap;
            } else {
                if (!ObjectUtils.isEmpty(allClientPaymentMap)) {
                    for (Integer paymentId : allClientPaymentMap.keySet()) {
                        GlPaymentResult payment = paymentMap.get(paymentId);
                        if (payment == null) {
                            payment = allClientPaymentMap.get(paymentId);
                        } else {
                            List<GlPaymentMerchantResult> merchantList = payment.getMerchantList();
                            if (merchantList == null) {
                                merchantList = new ArrayList<>();
                            }
                            GlPaymentResult allPayment = allClientPaymentMap.get(paymentId);
                            if (allPayment.getMerchantList() != null) {
                                merchantList.addAll(allPayment.getMerchantList());
                            }
                            payment.setMerchantList(merchantList);
                        }
                        paymentMap.put(paymentId, payment);
                    }
                }
            }
            if (ObjectUtils.isEmpty(paymentMap)) {
                redisService.delHashValue(key + levelId, String.valueOf(clientType));
            } else {
                redisService.delHashValue(key + levelId, String.valueOf(clientType));
                redisService.putHashValue(key + levelId, String.valueOf(clientType), new ArrayList<>(paymentMap.values()));
            }
        }
    }


    /**
     * 1、处理层级显示
     * 2、赋值应用当日收款上限、当日收款金额
     *
     * @param listDO
     * @return
     */
    public MerchantAppPage<PaymentMerchantAppDO> pageList(MerchantAccountAppQueryDO listDO) {
        //查询商户应用
        PageInfo<GlPaymentMerchantApp> result = getPageList(listDO);
        Date now = new Date();
        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(now, DateUtils.YYYYMMDD);

        List<GlPaymentMerchantApp> appList = result.getList();
        List<Integer> merchantIds = appList.stream().map(GlPaymentMerchantApp::getMerchantId)
                .distinct().collect(Collectors.toList());
        List<GlPaymentMerchantFee> fees = glPaymentMerchantFeeBusiness.findByMerchantIds(merchantIds);

        List<Integer> levelIds = appList.stream().filter(a -> StringUtils.isNotBlank(a.getLevelId()))
                .map(a -> a.getLevelId().split(","))
                .filter(levels -> levels.length > 0)
                .map(levels -> Integer.parseInt(levels[0]))
                .distinct().collect(Collectors.toList());
        List<GlFundUserlevel> levels = glFundUserlevelBusiness.findByLevelIds(levelIds);

        List<GlPaymentMerchantaccount> accounts = new ArrayList<>(merchantIds.size());
        List<GlPaymentMerchantaccount> merchantCaches = merchantIds.stream()
                .map(id -> redisService.getHashObject(key, String.valueOf(id), GlPaymentMerchantaccount.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        accounts.addAll(merchantCaches);
        String noneMerchantIds = StringUtils.join(merchantIds.stream()
                .filter(id -> merchantCaches.stream().noneMatch(m -> id.equals(m.getMerchantId())))
                .collect(Collectors.toList()), ",");
        if (StringUtils.isNotBlank(noneMerchantIds)) {
            accounts.addAll(glPaymentMerchantaccountBusiness.findByIds(noneMerchantIds));
        }
        //单位时间轮询内-进单总量
        Integer actualOrder = 0;
        //充值轮训周期时间（单位为分）
        Integer cycleTime = redisService.get(RedisKeyHelper.RECHARGE_CYCLE_TIME,Integer.class);
        Date cycleStart = glRechargeBusiness.getCycleStart(cycleTime, now);


        List<PaymentMerchantAppDO> list = new ArrayList<>();
        for (GlPaymentMerchantApp merchantApp : appList) {
            PaymentMerchantAppDO appDO = new PaymentMerchantAppDO();
            BeanUtils.copyProperties(merchantApp, appDO);

            String appKey = String.format(KeyConstant.FUND_RECHARGE_ROTATION, merchantApp.getId(), cycleStart.getTime());
            String count = redisService.get(appKey);
            actualOrder +=  StringUtils.isEmpty(count) ? 0 : Integer.valueOf(count);

            String[] level = appDO.getLevelId().split(",");
            if (level.length != 0) {
                levels.stream().filter(l -> l.getLevelId().equals(Integer.parseInt(level[0]))).findFirst()
                        .ifPresent(l -> appDO.setLevelName(String.format("%s 等%d个层级", l.getName(), level.length)));
            }

            appDO.setSuccessAmount(BigDecimal.ZERO);
            accounts.stream().filter(a -> a.getMerchantId().equals(merchantApp.getMerchantId())).findFirst()
                    .ifPresent(a -> {
                        int dailyLimit = ObjectUtils.isEmpty(a.getDailyLimit()) ? 0 : a.getDailyLimit();
                        appDO.setDailyLimit(BigDecimal.valueOf(dailyLimit));
                        long successAmount = ObjectUtils.isEmpty(a.getSuccessAmount()) ? 0 : a.getSuccessAmount();
                        appDO.setSuccessAmount(BigDecimal.valueOf(successAmount));
                    });

            // 金额范围
            fees.stream().filter(f -> f.getMerchantId().equals(merchantApp.getMerchantId()))
                    .filter(f -> f.getPaymentId().equals(merchantApp.getPaymentId()))
                    .filter(f -> f.getLimitType().equals(merchantApp.getLimitType()))
                    .findFirst()
                    .ifPresent(f -> {
                        appDO.setFeeRate(f.getFeeRate());
                        appDO.setMaxFee(f.getMaxFee());
                        appDO.setMinAmount(f.getMinAmount());
                        appDO.setMaxAmount(f.getMaxAmount());
                    });
            appDO.setPaymentName(FundLanguageUtils.getPaymentName(appDO.getPaymentId(),appDO.getPaymentName(),listDO.getLanguage()))    ;
            list.add(appDO);
        }
        MerchantAppPage<PaymentMerchantAppDO> pageInfo = new MerchantAppPage(list);
        pageInfo.setTotal(result.getTotal());
        pageInfo.setTotalOrder(list.stream().collect(Collectors.summingInt(PaymentMerchantAppDO::getCycleCount)));
        pageInfo.setActualOrder(actualOrder);
        return pageInfo;
    }


    /**
     * 新增三方商户应用
     *
     * @param addDO
     * @param admin
     * @throws GlobalException
     */
    public void save(MerchantAccountAppAddDO addDO, GlAdminDO admin, Integer useMode) throws GlobalException {
        GlPayment payment = glPaymentBusiness.findById(addDO.getPaymentId());
        if (null == payment) {
            throw new GlobalException(ResultCode.DATA_ERROR, "支付方式不支持");
        }

        GlPaymentMerchantaccount account = glPaymentMerchantaccountBusiness.findOne(addDO.getMerchantId());
        if (null == account) {
            throw new GlobalException(ResultCode.DATA_ERROR, "收款账号不存在或已停用");
        }

        List<GlPaymentMerchantApp> dbList = this.findList(addDO.getPaymentId(), addDO.getMerchantId(), null, useMode, null);
        if (dbList != null && !dbList.isEmpty()) {
            throw new GlobalException(ResultCode.DATA_ERROR, "此应用已存在");
        }
        //校验用户层级
        if (StringUtils.isNotEmpty(addDO.getLevelId())) {
            checkUserLevel(addDO.getLevelId());
        }

        //校验快捷金额
        String quickAmountStr = null;
        if (Arrays.asList(FundConstant.PaymentType.QUICK_ALI_PAY, FundConstant.PaymentType.QUICK_WECHAT_PAY,
                FundConstant.PaymentType.PHONECARD_PAY,FundConstant.PaymentType.QUICK_QQ_PAY).contains(addDO.getPaymentId())) {
            quickAmountStr = checkQuickAmount(addDO.getQuickAmount());
        }

        //校验商户应用金额区间
//        checkAmountRange(addDO.getLimitType(), addDO.getMinAmount(), addDO.getMaxAmount());

        Date now = new Date();
        GlPaymentMerchantApp merchant = new GlPaymentMerchantApp();
        merchant.setPaymentId(payment.getPaymentId());
        merchant.setPaymentName(payment.getPaymentName());
        merchant.setChannelId(account.getChannelId());
        merchant.setChannelName(account.getChannelName());
        merchant.setMerchantId(account.getMerchantId());
        merchant.setMerchantCode(account.getMerchantCode());
        merchant.setLevelId(addDO.getLevelId());
        merchant.setLimitType(addDO.getLimitType());
        merchant.setClientType(addDO.getClientType());
        merchant.setUseMode(useMode);
        merchant.setQuickAmount(quickAmountStr);
        merchant.setRemark(addDO.getRemark());
        merchant.setStatus(1);
        merchant.setOpenStatus(account.getStatus());
        merchant.setMerchantFee(addDO.getMerchantFee());
        merchant.setMerchantFeeType(addDO.getMerchantFeeType());
        merchant.setCreator(admin.getUsername());
        merchant.setCreateDate(now);
        merchant.setLastOperator(admin.getUsername());
        merchant.setLastUpdate(now);
        merchant.setMaxCount(addDO.getMaxCount());
        merchant.setCycleCount(addDO.getCycleCount());
        merchant.setCyclePriority(addDO.getCyclePriority());
        merchant.setNameType(StringUtils.join(addDO.getNameType(),","));
        merchant.setVipLevel(StringUtils.join(addDO.getVipLevel(),","));
        merchant.setCoin(payment.getCoin());
        glPaymentMerchantAppMapper.insertSelective(merchant);
    }

    /**
     * 编辑三方商户应用
     *
     * @param editDO
     * @param admin
     * @throws GlobalException
     */
    public void edit(MerchantAccountAppEditDO editDO, GlAdminDO admin) throws GlobalException {
        GlPaymentMerchantApp paymentMerchantApp = findById(editDO.getId());
        if (null == paymentMerchantApp) {
            throw new GlobalException(ResultCode.DATA_ERROR, "第三方充值应用不存在");
        }

        Integer useMode = paymentMerchantApp.getUseMode();
        List<GlPaymentMerchantApp> dbList = findList(editDO.getPaymentId(), editDO.getMerchantId(), editDO.getClientType(), useMode, null);
        if (dbList != null && !dbList.isEmpty()) {
            for (GlPaymentMerchantApp app : dbList) {
                if (!app.getId().equals(editDO.getId())) {
                    throw new GlobalException(ResultCode.DATA_ERROR);
                }
            }
        }

        GlPayment payment = glPaymentBusiness.findById(editDO.getPaymentId());
        if (null == payment) {
            throw new GlobalException(ResultCode.DATA_ERROR, "支付方式不支持");
        }

        GlPaymentMerchantaccount account = glPaymentMerchantaccountBusiness.findOne(editDO.getMerchantId());
        if (null == account) {
            throw new GlobalException(ResultCode.DATA_ERROR, "收款账号不存在或已停用");
        }
        //校验快捷金额
        String quickAmountStr = null;
        if (Arrays.asList(FundConstant.PaymentType.QUICK_ALI_PAY, FundConstant.PaymentType.QUICK_WECHAT_PAY,FundConstant.PaymentType.PHONECARD_PAY
                ,FundConstant.PaymentType.QUICK_QQ_PAY).contains(editDO.getPaymentId())) {
            quickAmountStr = checkQuickAmount(editDO.getQuickAmount());
        }

        //校验用户层级
        if (StringUtils.isNotEmpty(editDO.getLevelId())) {
            checkUserLevel(editDO.getLevelId());
        }

        //校验商户应用金额区间
//        checkAmountRange(editDO.getLimitType(), editDO.getMinAmount(), editDO.getMaxAmount());

        GlPaymentMerchantApp merchant = new GlPaymentMerchantApp();
        merchant.setId(editDO.getId());
        merchant.setQuickAmount(quickAmountStr);
        merchant.setClientType(editDO.getClientType());
        merchant.setLevelId(editDO.getLevelId());
        merchant.setOpenStatus(account.getStatus());
        merchant.setMerchantFee(editDO.getMerchantFee());
        merchant.setMerchantFeeType(editDO.getMerchantFeeType());
        merchant.setRemark(editDO.getRemark());
        merchant.setLastOperator(admin.getUsername());
        merchant.setLastUpdate(new Date());
        merchant.setMaxCount(editDO.getMaxCount());
        merchant.setCycleCount(editDO.getCycleCount());
        merchant.setCyclePriority(editDO.getCyclePriority());
        merchant.setNameType(StringUtils.join(editDO.getNameType(), ","));
        merchant.setVipLevel(StringUtils.join(editDO.getVipLevel(),","));
        glPaymentMerchantAppMapper.updateByPrimaryKeySelective(merchant);
    }

    /**
     * 批量更新
     *
     * @param editStatusDO
     * @param admin
     * @throws GlobalException
     */
    public void updateStatus(MerchantAccountAppEditStatusDO editStatusDO, GlAdminDO admin) throws GlobalException {
        editStatusDO.setStatus(editStatusDO.getStatus() == 0 ? 0 : 1);

        //批量更新商户应用
        List<GlPaymentMerchantApp> merchantAppList = new ArrayList<>();

        Date now = new Date();
        for (Integer id : editStatusDO.getMerchantAppIds()) {

            GlPaymentMerchantApp dbMer = this.findById(id);
            if (null == dbMer) {
                throw new GlobalException(ResultCode.DATA_ERROR, "第三方充值应用不存在");
            }

            GlPaymentMerchantApp mer = new GlPaymentMerchantApp();
            mer.setId(id);
            mer.setStatus(editStatusDO.getStatus());
            mer.setLastUpdate(now);
            mer.setLastOperator(admin.getUsername());

            merchantAppList.add(mer);
        }

        for (GlPaymentMerchantApp merchantApp : merchantAppList) {
            glPaymentMerchantAppMapper.updateByPrimaryKeySelective(merchantApp);
        }

        //修改充值应用后，更新所有层级的充值方式
        updatePaymentCache();
    }


    public void updateRecommend(MerchantAccountAppEditRecommendDO recommendDO, GlAdminDO admin) throws GlobalException {
        GlPaymentMerchantApp dbMer = findById(recommendDO.getId());
        if (null == dbMer) {
            throw new GlobalException("第三方充值应用不存在");
        }

        GlPaymentMerchantApp merchantApp = new GlPaymentMerchantApp();
        merchantApp.setId(recommendDO.getId());
        merchantApp.setRecommendStatus(recommendDO.getRecommendStatus());
        merchantApp.setLastOperator(admin.getUsername());
        merchantApp.setLastUpdate(new Date());
        glPaymentMerchantAppMapper.updateByPrimaryKeySelective(merchantApp);

        //修改充值应用后，更新所有层级的充值方式
        updatePaymentCache();

    }


    public void updateTopping(MerchantAccountAppEditTopStatusDO topStatusDO, GlAdminDO admin) throws GlobalException {
        GlPaymentMerchantApp dbMer = findById(topStatusDO.getId());
        if (null == dbMer) {
            throw new GlobalException("第三方充值应用不存在");
        }

        GlPaymentMerchantApp merchantApp = new GlPaymentMerchantApp();
        merchantApp.setId(topStatusDO.getId());
        merchantApp.setTopStatus(topStatusDO.getTopStatus());
        merchantApp.setTopDate(new Date());
        merchantApp.setLastOperator(admin.getUsername());
        merchantApp.setLastUpdate(new Date());
        glPaymentMerchantAppMapper.updateByPrimaryKeySelective(merchantApp);

        //修改充值应用后，更新所有层级的充值方式
        updatePaymentCache();

    }


    public void delete(Integer id, GlAdminDO admin) throws GlobalException {
        GlPaymentMerchantApp dbMer = findById(id);
        if (null == dbMer) {
            throw new GlobalException("第三方充值应用不存在");
        }

        GlPaymentMerchantApp mer = new GlPaymentMerchantApp();
        mer.setId(id);
        mer.setStatus(ProjectConstant.CommonStatus.DELETE);
        mer.setLastUpdate(new Date());
        mer.setLastOperator(admin.getUsername());
        glPaymentMerchantAppMapper.updateByPrimaryKeySelective(mer);
        //删除充值应用后，更新所有层级的充值方式
        updatePaymentCache();
    }


    //校验快捷金额
    public String checkQuickAmount(String quickAmount) throws GlobalException {
        StringBuffer quickAmountStr = new StringBuffer();
        if (StringUtils.isEmpty(quickAmount)) {
            throw new GlobalException("极速通道类型，快捷金额字段不能为空");
        }
        try {
            List<String> temp = Arrays.asList(quickAmount.trim().split(","));
            temp.forEach(t -> {
                Integer amount = Integer.parseInt(t.trim());
                quickAmountStr.append(amount).append(",");
            });
            return quickAmountStr.deleteCharAt(quickAmountStr.length() - 1).toString();
        } catch (Exception e) {
            throw new GlobalException("快捷金额输入有误");
        }
    }


    //校验用户层级
    private void checkUserLevel(String levelId) throws GlobalException {
        String[] levelIds = levelId.split(",");
        for (String id : levelIds) {
            GlFundUserlevel userlevel = glFundUserlevelBusiness.findById(id);
            if (null == userlevel) {
                throw new GlobalException("层级不存在或已删除");
            }
        }
    }


    //校验商户应用转账金额
    private void checkAmountRange(Integer limitType, BigDecimal minAmount, BigDecimal maxAmount) throws GlobalException {
        RechargeCommonAmountDO amountDO = null;
        if (limitType == FundConstant.LimitType.NORMAL) {
            amountDO = redisService.get(RedisKeyHelper.PAYMENT_NORMAL_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        } else if (limitType == FundConstant.LimitType.LARGE) {
            amountDO = redisService.get(RedisKeyHelper.PAYMENT_LARGE_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        } else if (limitType == FundConstant.LimitType.PROXY) {
            amountDO = redisService.get(RedisKeyHelper.PAYMENT_PROXY_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        }
        if (ObjectUtils.isEmpty(amountDO)) {
            throw new GlobalException("通用金额未配置");
        }
        if (limitType != FundConstant.LimitType.PROXY &&
                (minAmount.compareTo(amountDO.getMinAmount()) < 0
            || maxAmount.compareTo(amountDO.getMaxAmount()) > 0)) {
            throw new GlobalException("转账金额超出通用金额设置");
        }
    }

    public Integer getLevelMerchantCount(Integer levelId) {
        return glPaymentMerchantAppMapper.getLevelMerchantCount(levelId);
    }

    public List<GlPaymentMerchantApp> getActivateMerchant() {
        return glPaymentMerchantAppMapper.getActivateMerchant();
    }

    /**
     * 查询适用的充值商户应用
     * @param useMode
     * @return
     */
    public List<GlPaymentMerchantApp> findByUseMode(UseModeEnum useMode){
        return glPaymentMerchantAppMapper.findByUseMode(useMode.getCode());
    }
}
