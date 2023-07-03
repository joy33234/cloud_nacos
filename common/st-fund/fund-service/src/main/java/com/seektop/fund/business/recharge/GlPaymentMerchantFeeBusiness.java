package com.seektop.fund.business.recharge;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.controller.backend.param.recharge.RechargeCommonAmountDO;
import com.seektop.fund.controller.backend.param.recharge.fee.ChannelBankEditDO;
import com.seektop.fund.controller.backend.param.recharge.fee.MerchantFeeEditDO;
import com.seektop.fund.controller.backend.param.recharge.fee.MerchantFeeListDO;
import com.seektop.fund.controller.backend.param.recharge.fee.PaymentChannelBankEditDO;
import com.seektop.fund.mapper.GlPaymentMerchantFeeMapper;
import com.seektop.fund.model.GlPayment;
import com.seektop.fund.model.GlPaymentChannelBank;
import com.seektop.fund.model.GlPaymentMerchantFee;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class GlPaymentMerchantFeeBusiness extends AbstractBusiness<GlPaymentMerchantFee> {

    @Autowired
    private GlPaymentMerchantFeeMapper glPaymentMerchantFeeMapper;

    @Resource
    private RedisService redisService;
    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantaccountBusiness;
    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;
    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    // todo
    public Map<String, Object> listFee(MerchantFeeListDO dto) throws GlobalException {
        Map<String, Object> resultData = Maps.newHashMap();
        //普通和大额支付通道设置
        if (dto.getLimitType() == 0 || dto.getLimitType() == 1) {
            List<GlPayment> paymentList = glPaymentBusiness.findAll();
            if (null == dto.getMerchantId()) {
                dto.setMerchantId(glPaymentMerchantaccountBusiness.getFirstMerchantId(dto.getLimitType()));
            }
            List<GlPaymentMerchantFee> feeList = this.findList(dto.getLimitType(), dto.getMerchantId(), null);

            GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountBusiness.findById(dto.getMerchantId());
            if (null == merchantaccount) {
                throw new GlobalException(ResultCode.DATA_ERROR, "三方商户异常");
            }

            List<GlPaymentMerchantFee> result = this.getMerchatFeeList(paymentList, feeList, merchantaccount);
            resultData.put("data", result);
        }

        //快捷金额
        RechargeCommonAmountDO commonAmountDO = null;
        if (dto.getLimitType() == FundConstant.LimitType.NORMAL) {
            commonAmountDO = redisService.get(RedisKeyHelper.PAYMENT_NORMAL_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        } else if (dto.getLimitType() == FundConstant.LimitType.LARGE) {
            commonAmountDO = redisService.get(RedisKeyHelper.PAYMENT_LARGE_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        } else if (dto.getLimitType() == FundConstant.LimitType.PROXY) {
            commonAmountDO = redisService.get(RedisKeyHelper.PAYMENT_PROXY_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        }
        if (ObjectUtils.isEmpty(commonAmountDO)){
            commonAmountDO = new RechargeCommonAmountDO();
        }

        BigDecimal paymentRate = redisService.get(RedisKeyHelper.PAYMENT_USDT_RATE, BigDecimal.class);
        if (!ObjectUtils.isEmpty(paymentRate)) {
            commonAmountDO.setRate(paymentRate);
        }
        resultData.put("commonAmount", commonAmountDO);

        return resultData;
    }


    public void update(MerchantFeeEditDO editDO, GlAdminDO admin) throws GlobalException {
        if (editDO.getMinAmount().compareTo(editDO.getMaxAmount()) == 1
                || editDO.getMinAmount().compareTo(editDO.getMaxAmount()) == 0) {
            throw new GlobalException(ResultCode.DATA_ERROR, "充值下限金额不能大于等于充值上限金额");
        }
        GlPayment payment = glPaymentBusiness.findById(editDO.getPaymentId());
        if (null == payment) {
            throw new GlobalException(ResultCode.DATA_ERROR, "支付方式不存在");
        }
        GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountBusiness.findById(editDO.getMerchantId());
        if (null == merchantaccount) {
            throw new GlobalException(ResultCode.DATA_ERROR, "三方商户不存在");
        }

        GlPaymentMerchantFee merchantFee = DtoUtils.transformBean(editDO, GlPaymentMerchantFee.class);
        merchantFee.setMerchantCode(merchantaccount.getMerchantCode());
        merchantFee.setChannelId(merchantaccount.getChannelId());
        merchantFee.setChannelName(merchantaccount.getChannelName());
        merchantFee.setPaymentName(payment.getPaymentName());
        if (this.saveOrUpdateMerchantFee(merchantFee, admin.getUsername())) {
            glPaymentMerchantAppBusiness.updatePaymentCache();
        } else {
            throw new GlobalException(ResultCode.DATA_ERROR, "修改充值金额设置失败");
        }
    }

    public void updateAmount(RechargeCommonAmountDO rangeAmountDO) {
        if (rangeAmountDO.getLimitType() == FundConstant.LimitType.NORMAL) {
            redisService.set(RedisKeyHelper.PAYMENT_NORMAL_COMMON_AMOUNT, rangeAmountDO, -1);
        } else if (rangeAmountDO.getLimitType() == FundConstant.LimitType.LARGE) {
            redisService.set(RedisKeyHelper.PAYMENT_LARGE_COMMON_AMOUNT, rangeAmountDO, -1);
        } else if (rangeAmountDO.getLimitType() == FundConstant.LimitType.PROXY) {
            redisService.set(RedisKeyHelper.PAYMENT_PROXY_COMMON_AMOUNT, rangeAmountDO, -1);
        }

    }


    private boolean saveOrUpdateMerchantFee(GlPaymentMerchantFee merchantFee, String adminName) {
        Date now = new Date();
        if (merchantFee.getFeeId() == 0) {
            merchantFee.setFeeId(null);
            merchantFee.setCreateDate(now);
            merchantFee.setCreator(adminName);
            glPaymentMerchantFeeMapper.insertSelective(merchantFee);
        } else {
            GlPaymentMerchantFee originData = glPaymentMerchantFeeMapper.selectByPrimaryKey(merchantFee.getFeeId());
            if (null == originData) {
                return false;
            }
            merchantFee.setLastUpdate(now);
            merchantFee.setLastOperator(adminName);
            glPaymentMerchantFeeMapper.updateByPrimaryKeySelective(merchantFee);
        }
        return true;
    }

    public GlPaymentMerchantFee findFee(Integer limitType, Integer merchantId, Integer paymentId) {
        List<GlPaymentMerchantFee> resultList = findList(limitType, merchantId, paymentId);
        if (ObjectUtils.isEmpty(resultList)) {
            return null;
        }
        return resultList.get(0);
    }

    public List<GlPaymentMerchantFee> findList(Integer limitType, Integer merchantId, Integer paymentId) {
        Condition con = new Condition(GlPaymentMerchantFee.class);
        Condition.Criteria criteria = con.createCriteria();
        if (!ObjectUtils.isEmpty(limitType)) {
            criteria.andEqualTo("limitType", limitType);
        }
        if (!ObjectUtils.isEmpty(merchantId)) {
            criteria.andEqualTo("merchantId", merchantId);
        }
        if (!ObjectUtils.isEmpty(paymentId)) {
            criteria.andEqualTo("paymentId", paymentId);
        }
        return glPaymentMerchantFeeMapper.selectByCondition(con);
    }

    public List<GlPaymentMerchantFee> findByMerchantIds(List<Integer> merchantIds) {
        if(CollectionUtils.isEmpty(merchantIds))
            return Lists.newArrayList();
        return glPaymentMerchantFeeMapper.findByMerchantIds(merchantIds);
    }

    private List<GlPaymentMerchantFee> getMerchatFeeList(List<GlPayment> paymentList, List<GlPaymentMerchantFee> feeList, GlPaymentMerchantaccount merchantaccount) {
        if (paymentList.isEmpty()) {
            return Collections.emptyList();
        }
        List<GlPaymentMerchantFee> result = new ArrayList<>(paymentList.size());

        outer:
        for (GlPayment payment : paymentList) {
            GlPaymentMerchantFee paymentFee = new GlPaymentMerchantFee();
            paymentFee.setFeeRate(BigDecimal.valueOf(-1));
            paymentFee.setMaxAmount(BigDecimal.valueOf(-1));
            paymentFee.setMaxFee(BigDecimal.valueOf(-1));
            paymentFee.setMinAmount(BigDecimal.valueOf(-1));
            inner:
            for (GlPaymentMerchantFee fee : feeList) {
                if (payment.getPaymentId().equals(fee.getPaymentId())) {
                    paymentFee.setFeeRate(fee.getFeeRate());
                    paymentFee.setMaxAmount(fee.getMaxAmount());
                    paymentFee.setMaxFee(fee.getMaxFee());
                    paymentFee.setMinAmount(fee.getMinAmount());
                    if (!ObjectUtils.isEmpty(fee.getFeeId())) {
                        paymentFee.setFeeId(fee.getFeeId());
                    }
                    break inner;
                }
            }
            paymentFee.setChannelId(merchantaccount.getChannelId());
            paymentFee.setChannelName(merchantaccount.getChannelName());
            paymentFee.setMerchantId(merchantaccount.getMerchantId());
            paymentFee.setMerchantCode(merchantaccount.getMerchantCode());
            paymentFee.setPaymentId(payment.getPaymentId());
            paymentFee.setPaymentName(payment.getPaymentName());
            result.add(paymentFee);
        }
        return result;
    }

    /**
     * 网银支付-银行卡限额编辑
     *
     * @param editDO
     * @param adminDO
     * @throws GlobalException
     */
    public void updateBankLimit(ChannelBankEditDO editDO, GlAdminDO adminDO) throws GlobalException {

        if (null == editDO.getBankList() || editDO.getBankList().size() == 0) {
            throw new GlobalException(ResultCode.DATA_ERROR, "最少启用一张银行卡");
        }

        GlPaymentChannelBank bankCard = glPaymentChannelBankBusiness.findById(editDO.getBankList().get(0).getPaybankId());
        if (null == bankCard) {
            throw new GlobalException(ResultCode.DATA_ERROR, "查询数据失败");
        }

        List<GlPaymentChannelBank> allBankList = glPaymentChannelBankBusiness.findList(bankCard.getChannelId());
        for (GlPaymentChannelBank bb : allBankList) {
            Boolean modifyFlag = false;
            for (PaymentChannelBankEditDO bank : editDO.getBankList()) {
                if (bank.getMinAmount().compareTo(BigDecimal.ZERO) == -1
                        || bank.getMaxAmount().compareTo(BigDecimal.ZERO) == -1
                        || bank.getMaxAmount().compareTo(bank.getMinAmount()) == -1) {
                    throw new GlobalException(ResultCode.DATA_ERROR, "参数异常：银行卡限额错误");
                }
                if (glPaymentChannelBankBusiness.findById(bank.getPaybankId()) == null) {
                    throw new GlobalException(ResultCode.DATA_ERROR, "数据异常：银行卡不存在");
                }
                if (bb.getPaybankId().equals(bank.getPaybankId())) {
                    modifyFlag = true;
                    bb.setStatus(0);
                    bb.setMinAmount(bank.getMinAmount());
                    bb.setMaxAmount(bank.getMaxAmount());
                    break;
                }
            }
            if (!modifyFlag) {
                bb.setStatus(1);
            }
            bb.setLastOperator(adminDO.getUsername());
            bb.setLastUpdate(new Date());
        }
        glPaymentChannelBankBusiness.batchUpdate(allBankList);
        glPaymentMerchantAppBusiness.updatePaymentCache();
    }
}
