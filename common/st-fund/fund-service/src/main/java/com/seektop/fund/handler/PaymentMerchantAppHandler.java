package com.seektop.fund.handler;

import com.seektop.common.redis.RedisService;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantFeeBusiness;
import com.seektop.fund.controller.backend.param.recharge.app.MerchantAccountAppAddDO;
import com.seektop.fund.controller.backend.param.recharge.app.MerchantAccountAppEditDO;
import com.seektop.fund.controller.backend.param.recharge.fee.MerchantFeeEditDO;
import com.seektop.fund.enums.UseModeEnum;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlPaymentMerchantApp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PaymentMerchantAppHandler {

    @Autowired
    private GlPaymentMerchantAppBusiness merchantAppBusiness;
    @Resource
    private GlPaymentMerchantFeeBusiness merchantFeeBusiness;
    @Resource
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Resource
    private RedisService redisService;

    /**
     * 新增
     * @param addDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(MerchantAccountAppAddDO addDO, GlAdminDO admin) throws GlobalException {
        // 保存应用
        List<Integer> useModes = addDO.getUseModes();
        for (Integer useMode : useModes) {
            merchantAppBusiness.save(addDO, admin, useMode);
        }
        // 保存商户的费率及金额范围
        MerchantFeeEditDO editDO = new MerchantFeeEditDO();
        BeanUtils.copyProperties(addDO, editDO);
        editDO.setFeeId(0);
        Optional.ofNullable(merchantFeeBusiness
                .findFee(addDO.getLimitType(), addDO.getMerchantId(), addDO.getPaymentId()))
                .ifPresent(fee -> editDO.setFeeId(fee.getFeeId()));
        merchantFeeBusiness.update(editDO, admin);
    }

    /**
     * 更新
     * @param editDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(MerchantAccountAppEditDO editDO, GlAdminDO admin) throws GlobalException {
        merchantAppBusiness.edit(editDO, admin);
        // 保存商户的费率及金额范围
        MerchantFeeEditDO feeEditDO = new MerchantFeeEditDO();
        BeanUtils.copyProperties(editDO, feeEditDO);
        feeEditDO.setFeeId(0);
        Optional.ofNullable(merchantFeeBusiness
                .findFee(editDO.getLimitType(), editDO.getMerchantId(), editDO.getPaymentId()))
                .ifPresent(fee -> feeEditDO.setFeeId(fee.getFeeId()));
        merchantFeeBusiness.update(feeEditDO, admin);
    }

    /**
     * 更新代客层级开关
     */
    public void updateAgencyLevel() {
        // 总开关开启
        redisService.set(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG, 1, -1);
        List<GlPaymentMerchantApp> appList = merchantAppBusiness.findByUseMode(UseModeEnum.INSTEAD);
        List<String> strIds = new ArrayList<>();
        appList.stream().filter(a -> StringUtils.isNotBlank(a.getLevelId()))
                .map(a -> a.getLevelId().split(","))
                .map(Arrays::asList)
                .forEach(strIds::addAll);
        List<Integer> appLevelIds = strIds.stream().distinct()
                .map(Integer::parseInt).collect(Collectors.toList());
        List<GlFundUserlevel> levels = fundUserlevelBusiness.findAll();
        for (GlFundUserlevel level : levels) {
            Integer levelId = level.getLevelId();
            if (appLevelIds.stream().anyMatch(id -> id.equals(levelId))) { // 开启层级
                redisService.set(RedisKeyHelper.AGENCY_USER_LEVEL + levelId, 1, -1);
            }
            else { // 关闭层级
                redisService.delete(RedisKeyHelper.AGENCY_USER_LEVEL + levelId);
            }
        }
    }
}
