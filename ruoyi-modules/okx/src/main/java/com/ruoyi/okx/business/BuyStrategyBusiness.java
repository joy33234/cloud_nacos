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

//    public boolean check(OkxBuyRecord buyRecord, OkxCoin coin) {
//        OkxBuyStrategy buyStrategy = getById(buyRecord.getStrategyId());
//        if (coin.getUnit().compareTo(BigDecimal.ZERO) <= 0 || ObjectUtils.isEmpty(coin.getCount())) {
//            log.warn("买入校验策略-单位为0 coin:{}", JSON.toJSONString(coin));
//            return false;
//        }
//        BigDecimal times = coin.getCount().divide(coin.getUnit());
//        if (times.compareTo(new BigDecimal(buyStrategy.getHoldMaxTimes().intValue())) > 0) {
//            log.warn("不能高于最高手持倍数 coin:{}", coin.getCoin());
//            return false;
//        }
//        BigDecimal total = coin.getCount().add(buyRecord.getQuantity());
//        BigDecimal onlySellTimes = total.divide(coin.getUnit());
//        if (onlySellTimes.compareTo(new BigDecimal(buyStrategy.getHoldMaxTimes().intValue())) > 0) {
//            log.warn("状态变更为只读 coin:{},buyStrategy:{} ", JSON.toJSONString(coin), JSON.toJSONString(buyStrategy));
//            coin.setStatus(CoinStatusEnum.ONYYSELL.getStatus());
//            coinBusiness.updateList(Arrays.asList(coin));
//        }
//        if (buyRecord.getAmount().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.BUY_MAX_USDT))) > 0) {
//            log.warn("买入金额高于最高买入值 account:Id{}, amount:{}", buyRecord.getAccountId(), buyRecord.getAmount());
//            return false;
//        }
//        if (this.buyRecordBusiness.hasBuy(buyRecord.getAccountId(), buyRecord.getStrategyId(), coin.getCoin()) == true) {
//            log.warn("买入失败-该策略已买 account:{} coin:{}", buyRecord.getAccountId(), coin.getCoin());
//            return false;
//        }
//        return true;
//    }

}
