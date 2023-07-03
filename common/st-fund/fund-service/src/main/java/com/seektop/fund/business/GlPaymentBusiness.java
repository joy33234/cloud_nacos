package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.mapper.GlPaymentMapper;
import com.seektop.fund.mapper.GlPaymentMerchantAppMapper;
import com.seektop.fund.mapper.GlPaymentMerchantaccountMapper;
import com.seektop.fund.model.GlPayment;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlPaymentBusiness extends AbstractBusiness<GlPayment> {

    @Resource
    private GlPaymentMapper glPaymentMapper;
    @Resource
    private GlPaymentMerchantAppMapper glPaymentMerchantAppMapper;
    @Resource
    private GlPaymentMerchantaccountMapper glPaymentMerchantaccountMapper;

    public PageInfo<GlPayment> findList(Integer page, Integer size, String coin) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(GlPayment.class);
        Example.Criteria criteria = condition.createCriteria();
        if (StringUtils.hasText(coin)) {
            criteria.andEqualTo("coin", coin);
        }
        condition.setOrderByClause(" sort asc");
        return new PageInfo<>(findByCondition(condition));
    }

    public String getPaymentName(Integer paymentId) {
        GlPayment payment = glPaymentMapper.selectByPrimaryKey(paymentId);
        if (null != payment) {
            return payment.getPaymentName();
        }
        return null;
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void updatePaymentMerchant(GlPaymentMerchantaccount merchant) throws GlobalException {
        try {
            glPaymentMerchantaccountMapper.updateByPrimaryKeySelective(merchant);
            if (1 == merchant.getStatus() || 2 == merchant.getStatus()) {
                // 禁用账号时，相关的充值应用、提现应用也要禁用
                updateByAccount(merchant);
            }
        } catch (Exception e) {
            log.error("updatePaymentMerchant error.", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }



    private void updateByAccount(GlPaymentMerchantaccount account) {
        Condition con = new Condition(GlPaymentMerchantApp.class);
        con.createCriteria().andEqualTo("merchantId", account.getMerchantId()).andNotIn("paymentId",
                Arrays.asList(FundConstant.PaymentType.BANKCARD_TRANSFER, FundConstant.PaymentType.ALI_TRANSFER, FundConstant.PaymentType.WECHAT_TRANSFER));
        List<GlPaymentMerchantApp> merchantList = glPaymentMerchantAppMapper.selectByCondition(con);
        if (null != merchantList) {
            for (GlPaymentMerchantApp merchant : merchantList) {
                merchant.setLastOperator(account.getLastOperator());
                merchant.setLastUpdate(account.getLastUpdate());
                merchant.setStatus(account.getStatus());
                glPaymentMerchantAppMapper.updateByPrimaryKeySelective(merchant);
            }
        }
    }

    /**
     * 支付方式排序
     * @param payments
     */
    public void sort(List<GlPaymentResult> payments){
        if (CollectionUtils.isEmpty(payments))
            return;

        String paymentIds = payments.stream()
                .map(p -> String.valueOf(p.getPaymentId()))
                .distinct().collect(Collectors.joining(","));
        List<GlPayment> list = findByIds(paymentIds);
        Map<Integer, Integer> sortMap = list.stream()
                .collect(Collectors.toMap(GlPayment::getPaymentId, GlPayment::getSort));
        Integer max = list.stream()
                .max(Comparator.comparingInt(GlPayment::getSort))
                .map(GlPayment::getSort)
                .orElse(0) + 1;
        payments.sort(Comparator.comparingInt(p -> Optional.ofNullable(sortMap.get(p.getPaymentId())).orElse(max)));
    }
}
