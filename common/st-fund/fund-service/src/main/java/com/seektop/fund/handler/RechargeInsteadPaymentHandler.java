package com.seektop.fund.handler;

import com.google.common.collect.Lists;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAccountBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantFeeBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.result.GlPaymentBankResult;
import com.seektop.fund.controller.backend.result.GlPaymentMerchantResult;
import com.seektop.fund.controller.backend.result.GlPaymentNewResult;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.forehead.param.recharge.AgencyRechargePaymentInfoDO;
import com.seektop.fund.controller.partner.param.PaymentForm;
import com.seektop.fund.controller.partner.result.PaymentBankResponse;
import com.seektop.fund.controller.partner.result.PaymentMerchantResponse;
import com.seektop.fund.controller.partner.result.PaymentResponse;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.enums.UseModeEnum;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlRechargeHandlerManager;
import com.seektop.user.service.GlUserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RechargeInsteadPaymentHandler {

    @Resource
    private GlPaymentMerchantAppBusiness paymentMerchantAppBusiness;
    @Resource
    private GlPaymentMerchantFeeBusiness paymentMerchantFeeBusiness;
    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;
    @Resource
    private GlPaymentMerchantAccountBusiness paymentMerchantAccountBusiness;
    @Resource
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Resource
    private GlRechargeHandlerManager rechargeHandlerManager;
    @DubboReference(timeout = 3000)
    private GlUserService userService;
    @Autowired
    private RedisService redisService;

    /**
     * 获取代客充值方式返回结果
     *
     * @param form
     * @return
     */
    public Result getPayments(PaymentForm form) {
        RPCResponse<GlUserDO> rpcResponse = userService.findById(form.getUserId());
        if (RPCResponseUtils.isFail(rpcResponse) || ObjectUtils.isEmpty(rpcResponse.getData())) {
            return Result.newBuilder().fail(ResultCode.USER_NAME_NOT_EXIST).build();
        }
        return Result.genSuccessResult(getPaymentApps(form));
    }

    /**
     * 获取代客充值渠道
     * @param paymentInfo
     * @return
     */
    public Result paymentInfo(AgencyRechargePaymentInfoDO paymentInfo) {
        RPCResponse<GlUserDO> rpcResponse = userService.getUserInfoByUsername(paymentInfo.getUserName());
        if (RPCResponseUtils.isFail(rpcResponse)) {
            return Result.newBuilder().fail(ResultCode.INVALID_PARAM).build();
        }
        // 用户层级
        GlUserDO user = rpcResponse.getData();
        GlFundUserlevel level = fundUserlevelBusiness.getUserLevel(user.getId());
        GlPaymentNewResult result = new GlPaymentNewResult();
        PaymentForm form = new PaymentForm();
        form.setUserId(user.getId());
        form.setLevelId(level.getLevelId());
        form.setOsType(paymentInfo.getOsType());
        List<PaymentResponse> paymentApps = getPaymentApps(form);
        List<PaymentResponse> payments = null;
        // 根据金额筛选
        BigDecimal amount = paymentInfo.getAmount();
        if (!ObjectUtils.isEmpty(amount)) {
            payments = paymentApps.stream()
                    .filter(p -> p.getMerchants().stream()
                            .filter(m -> amount.compareTo(p.getMinAmount()) >= 0)
                            .anyMatch(m -> amount.compareTo(p.getMaxAmount()) <= 0))
                    .collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(payments)) {
            BigDecimal minAmount = paymentApps.stream()
                    .min(Comparator.comparing(PaymentResponse::getMinAmount))
                    .map(PaymentResponse::getMinAmount)
                    .orElse(BigDecimal.ZERO);
            String message = null;
            if (amount.compareTo(minAmount) < 0) {
                message = String.format("单笔最小充值金额为 %s 元", minAmount.setScale(0).toString());
            }
            BigDecimal maxAmount = paymentApps.stream()
                    .max(Comparator.comparing(PaymentResponse::getMaxAmount))
                    .map(PaymentResponse::getMaxAmount)
                    .orElse(BigDecimal.ZERO);
            if (amount.compareTo(maxAmount) > 0) {
                message = String.format("单笔最大充值金额为 %s 元", maxAmount.setScale(0).toString());
            }
            Result result1 = Result.newBuilder().fail(ResultCode.INVALID_PARAM).setMessage(message).build();
            result1.setKeyConfig(FundLanguageMvcEnum.RECHARGE_ORDER_AMOUNT_MIN_LIMIT);
            return result1;
        }

        List<GlPaymentResult> normal = payments.stream()
                .map(p -> {
                    GlPaymentResult r = new GlPaymentResult();
                    r.setPaymentId(p.getPaymentId());
                    r.setPaymentName(FundLanguageUtils.getPaymentName(p.getPaymentId(), p.getPaymentName(), paymentInfo.getLanguage()));
                    r.setLimitType(FundConstant.PaymentCache.NORMAL);
                    r.setMerchantList(p.getMerchants().stream()
                            .map(m -> {
                                GlPaymentMerchantResult mr = new GlPaymentMerchantResult();
                                BeanUtils.copyProperties(m, mr);
                                mr.setAppId(m.getId());
                                mr.setMerchantName(m.getChannelName());
                                mr.setBankList(m.getBanks().stream()
                                        .map(b -> {
                                            GlPaymentBankResult br = new GlPaymentBankResult();
                                            BeanUtils.copyProperties(b, br);
                                            br.setStatus(b.getStatus());
                                            return br;
                                        })
                                        .collect(Collectors.toList())
                                );
                                return mr;
                            })
                            .collect(Collectors.toList())
                    );
                    return r;
                })
                .collect(Collectors.toList());
        result.setNormal(normal);
        return Result.genSuccessResult(result);
    }

    /**
     * 获取代客充值的充值方式
     *
     * @return
     */
    public List<PaymentResponse> getPaymentApps(PaymentForm form) {
        // 代客充值且普通充值商户用户
        Map<Integer, List<GlPaymentMerchantApp>> groups = getGroups(form);
        List<PaymentResponse> payments = new ArrayList<>();
        groups.forEach((paymentId, apps) -> {
            GlPaymentMerchantApp merchantApp = apps.get(0);
            List<PaymentMerchantResponse> list = getMerchants(paymentId, apps, form);
            if (!CollectionUtils.isEmpty(list)) {
                // 支付方式的金额范围
                BigDecimal min = list.stream().min(Comparator.comparing(PaymentMerchantResponse::getMinAmount))
                        .map(PaymentMerchantResponse::getMinAmount).orElse(BigDecimal.ZERO);
                BigDecimal max = list.stream().max(Comparator.comparing(PaymentMerchantResponse::getMaxAmount))
                        .map(PaymentMerchantResponse::getMaxAmount).orElse(BigDecimal.ZERO);

                PaymentResponse pr = new PaymentResponse();
                pr.setPaymentId(merchantApp.getPaymentId());
                pr.setPaymentName(merchantApp.getPaymentName());
                pr.setMinAmount(min);
                pr.setMaxAmount(max);
                pr.setMerchants(list);
                rechargeHandlerManager.paymentSetting(pr);
                payments.add(pr);
            }
        });
        // 银行转账排前面
        payments.sort(Comparator.comparing(p -> FundConstant.PaymentType.BANKCARD_TRANSFER == p.getPaymentId() ? 0 : 1));
        return payments;
    }

    /**
     * 代客充值且普通充值商户用户的分组
     *
     * @return
     */
    private Map<Integer, List<GlPaymentMerchantApp>> getGroups(PaymentForm form) {
        // 代客充值且普通充值商户用户
        List<GlPaymentMerchantApp> appList = paymentMerchantAppBusiness.findByUseMode(UseModeEnum.INSTEAD);
        if (!ObjectUtils.isEmpty(form.getLevelId())) { // 层级的筛选
            appList = appList.stream()
                    .filter(a -> Arrays.stream(a.getLevelId().split(","))
                            .anyMatch(id -> id.equals(form.getLevelId().toString())))
                    .collect(Collectors.toList());
        }
        if (!ObjectUtils.isEmpty(form.getOsType())) { // 使用端筛选
            appList = appList.stream()
                    .filter(a -> a.getClientType().equals(-1) || a.getClientType().equals(form.getOsType()))
                    .collect(Collectors.toList());
        }
        //过滤掉快捷支付、极速充值
        return appList.stream()
                .filter(a -> FundConstant.PaymentCache.NORMAL == a.getLimitType())
                .filter(a -> !a.getPaymentId().equals(FundConstant.PaymentType.QUICK_PAY))
                .collect(Collectors.groupingBy(GlPaymentMerchantApp::getPaymentId));
    }

    /**
     * 筛选可用的通道
     *
     * @param paymentId
     * @param apps
     * @param form
     * @return
     */
    private List<PaymentMerchantResponse> getMerchants(Integer paymentId, List<GlPaymentMerchantApp> apps, PaymentForm form) {
        // 支付类型 - 手续费以及限额设置
        List<GlPaymentMerchantFee> fees = paymentMerchantFeeBusiness.findList(
                FundConstant.PaymentCache.NORMAL, null, paymentId);
        // 未配置充值金额限额以及手续费；不展示改通道
        // 配置充值限额0-0  不展示通道
        List<PaymentMerchantResponse> list = apps.stream().map(a -> {
            PaymentMerchantResponse r = new PaymentMerchantResponse();
            BeanUtils.copyProperties(a, r);
            return r;
        }).filter(r -> getFee(fees, r.getMerchantId()).isPresent())
                .collect(Collectors.toList());
        // 查询剩余收款金额和成功率
        setPaymentMerchants(list);
        // 过滤掉今日剩余收款金额小于0的通道
        // 按照成功率，剩余收款金额排序
        list = list.stream().filter(r -> r.getLeftAmount() > 0)
                .sorted(Comparator.comparing(PaymentMerchantResponse::getSuccessRate).reversed()
                        .thenComparing(PaymentMerchantResponse::getLeftAmount).reversed())
                .collect(Collectors.toList());
        // 按有失败排最后
        list.sort(Comparator.comparing(r -> isNotFail(r.getId(), form.getUserId()) ? 0 : 1));
        // 设置费率和金额范围和银行卡的参数
        setPaymentMerchants(list, fees, paymentId);
        // 通道名称
        for (int i = 0, len = list.size(); i < len; i++) {
            list.get(i).setAisleName("通道" + StringEncryptor.toCH(i+1));
        }
        return list;
    }

    /**
     * 剩余收款金额和成功率
     *
     * @param list
     */
    private void setPaymentMerchants(List<PaymentMerchantResponse> list) {
        List<GlPaymentMerchantaccount> mAccounts = paymentMerchantAccountBusiness.getMerchantAccountCache(list.stream()
                .map(PaymentMerchantResponse::getMerchantId)
                .distinct().collect(Collectors.toList()));
        list.forEach(payment -> {
            payment.setSuccessRate(0);
            payment.setLeftAmount(0L);
            mAccounts.stream().filter(a -> a.getMerchantId().equals(payment.getMerchantId()))
                    .findFirst()
                    .ifPresent(a -> {
                        // 剩余收款金额
                        payment.setLeftAmount(a.getDailyLimit().longValue());
                        if (null != a.getSuccessAmount()) {
                            payment.setLeftAmount(a.getDailyLimit() - a.getSuccessAmount());
                        }
                        // 通道成功率
                        if (null != a.getTotal() && a.getTotal() != 0) {
                            float successRate = (float) a.getSuccess() / (float) a.getTotal() * 10000;
                            payment.setSuccessRate(Math.round(successRate));
                        }
                    });
        });
    }

    /**
     * 不是失败的渠道
     *
     * @param appId
     * @param userId
     * @return
     */
    private boolean isNotFail(Integer appId, Integer userId) {
        //用户充值渠道失败记录
        String key = RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + userId;
        RedisResult<Integer> failList = redisService.getListResult(key, Integer.class);
        if (null == failList || CollectionUtils.isEmpty(failList.getListResult()))
            return true;
        return failList.getListResult().stream().noneMatch(id -> id.equals(appId));
    }

    /**
     * 配置充值金额限额以及手续费
     * 配置充值限额不为0-0的通道
     *
     * @param fees
     * @param merchantId
     * @return
     */
    private Optional<GlPaymentMerchantFee> getFee(List<GlPaymentMerchantFee> fees, Integer merchantId) {
        return fees.stream().filter(f -> f.getMerchantId().equals(merchantId))
                .filter(f -> f.getMinAmount().compareTo(BigDecimal.ZERO) != 0)
                .filter(f -> f.getMaxAmount().compareTo(BigDecimal.ZERO) != 0)
                .findFirst();
    }

    /**
     * 费率及金额范围
     *
     * @param r
     * @param fees
     */
    private void setFee(PaymentMerchantResponse r, List<GlPaymentMerchantFee> fees) {
        Optional<GlPaymentMerchantFee> feeOptional = getFee(fees, r.getMerchantId());
        feeOptional.ifPresent(fee -> {
            r.setFee(fee.getFeeRate());
            r.setMinAmount(fee.getMinAmount());
            r.setMaxAmount(fee.getMaxAmount());
        });
    }

    /**
     * 设置相关参数
     *
     * @param list
     * @param fees
     * @param paymentId
     */
    private void setPaymentMerchants(List<PaymentMerchantResponse> list, List<GlPaymentMerchantFee> fees, Integer paymentId) {
        if (CollectionUtils.isEmpty(list))
            return;

        List<Integer> channelIds = list.stream()
                .map(PaymentMerchantResponse::getChannelId)
                .distinct().collect(Collectors.toList());
        List<GlPaymentChannelBank> channelBanks = paymentChannelBankBusiness.findByChannelIds(channelIds);
        list.forEach(r -> {
            // 设置费率和金额范围
            setFee(r, fees);
            // 银行卡列表  网银支付的时候初始化
            List<PaymentBankResponse> banks = getBanks(paymentId, r.getChannelId(), channelBanks);
            r.setBanks(banks);
        });
    }

    /**
     * 银行卡列表
     *
     * @param paymentId
     * @param channelId
     * @param channelBankList
     * @return
     */
    private List<PaymentBankResponse> getBanks(Integer paymentId, Integer channelId, List<GlPaymentChannelBank> channelBankList) {
        List<PaymentBankResponse> banks = Lists.newArrayList();
        // 网银支付银行卡列表
        if (FundConstant.PaymentType.ONLINE_PAY == paymentId) {
            banks = getBanks(channelId, channelBankList);
        }
        // 银行卡转账银行卡列表
        else if (FundConstant.PaymentType.BANKCARD_TRANSFER == paymentId) {
            List<Integer> channelIds = Lists.newArrayList(
                    FundConstant.PaymentChannel.STORMPAY,
                    FundConstant.PaymentChannel.STPAYER
            );
            if (channelIds.stream().anyMatch(id -> id.equals(channelId))) {
                banks = getBanks(channelId, channelBankList);
            }
        }
        return banks;
    }

    /**
     * 银行信息
     * @param channelId
     * @param channelBankList
     * @return
     */
    private List<PaymentBankResponse> getBanks(Integer channelId, List<GlPaymentChannelBank> channelBankList){
        return channelBankList.stream()
                .filter(b -> b.getChannelId().equals(channelId))
                .filter(b -> b.getStatus() == ProjectConstant.CommonStatus.NORMAL)
                .map(bank -> {
                    PaymentBankResponse br = new PaymentBankResponse();
                    br.setStatus(bank.getStatus());
                    br.setBankId(bank.getBankId());
                    br.setBankName(bank.getBankName());
                    br.setMinAmount(bank.getMinAmount());
                    br.setMaxAmount(bank.getMaxAmount());
                    return br;
                }).collect(Collectors.toList());
    }
}
