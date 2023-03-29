package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.domain.OkxBuyStrategy;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.mapper.BuyStrategyMapper;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BuyStrategyBusiness extends ServiceImpl<BuyStrategyMapper, OkxBuyStrategy> {
    private static final Logger log = LoggerFactory.getLogger(BuyStrategyBusiness.class);

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private BuyStrategyMapper strategyMapper;

    @Resource
    private SettingService settingService;



}
