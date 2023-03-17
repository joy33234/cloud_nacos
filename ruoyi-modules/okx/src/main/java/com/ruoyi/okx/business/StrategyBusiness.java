package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.domain.OkxStrategy;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.enums.ModeTypeEnum;
import com.ruoyi.okx.enums.OkxOrdTypeEnum;
import com.ruoyi.okx.mapper.OkxStrategyMapper;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StrategyBusiness extends ServiceImpl<OkxStrategyMapper, OkxStrategy> {
    private static final Logger log = LoggerFactory.getLogger(StrategyBusiness.class);

    @Resource
    private OkxStrategyMapper mapper;

    @Resource
    private SettingService settingService;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private CoinBusiness coinBusiness;

   
    public List<OkxStrategy> list(OkxStrategy strategy) {
        LambdaQueryWrapper<OkxStrategy> wrapper = new LambdaQueryWrapper();
        if (strategy != null && strategy.getAccountId() != null) {
            wrapper.eq((null != strategy.getAccountId()), OkxStrategy::getAccountId, strategy.getAccountId());
        }
        if (strategy != null && strategy.getId() != null) {
            wrapper.eq((null != strategy.getId()), OkxStrategy::getId, strategy.getId());
        }
        if (StringUtils.isNotEmpty(strategy.getSettingIds())) {
            wrapper.like((null != strategy.getSettingIds()), OkxStrategy::getSettingIds, strategy.getSettingIds());
        }
        return mapper.selectList(wrapper);
    }

    public boolean save(OkxStrategy strategy) {
        return mapper.insert(strategy) > 0 ? true : false;
    }

    public boolean update(OkxStrategy strategy) {
        return mapper.updateById(strategy) > 0 ? true : false;
    }

    public boolean delete(OkxStrategy strategy) {
        return mapper.deleteById(strategy) > 0 ? true : false;
    }


    public boolean checkBuy(OkxBuyRecord buyRecord, OkxCoin coin) {

        //List<OkxSetting> okxSettings = this.listByStrategyId(buyRecord.getStrategyId());
        if (coin.getUnit().compareTo(BigDecimal.ZERO) <= 0 || ObjectUtils.isEmpty(coin.getCount())) {
            log.warn("买入校验策略-单位为0 coin:{}", JSON.toJSONString(coin));
            return false;
        }
        BigDecimal times = coin.getCount().divide(coin.getUnit());
        BigDecimal buyMaxTime = new BigDecimal(settingService.selectSettingByKey(OkxConstants.BUY_MAX_TIMES));
        if (times.compareTo(buyMaxTime) > 0) {
            log.warn("不能高于最高手持倍数 coin:{}", coin.getCoin());
            return false;
        }
        //网络系统 现有数量与买入数量不能超过最高手持数量
        //okxSettings.stream().filter(item -> item.getSettingValue().equals(ModeTypeEnum.GRID.getValue())).collect(Collectors.toList()).stream().findFirst().ifPresent(obj -> {
        BigDecimal total = coin.getCount().add(buyRecord.getQuantity());
        BigDecimal onlySellTimes = total.divide(coin.getUnit());
        if (onlySellTimes.compareTo(buyMaxTime) > 0) {
            log.warn("状态变更为只卖 coin:{},buyStrategy:{} ", JSON.toJSONString(coin));
            coin.setStatus(CoinStatusEnum.ONYYSELL.getStatus());
            coinBusiness.updateById(coin);
        }
        //});
        if (buyRecord.getAmount().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.BUY_MAX_USDT))) > 0) {
            log.warn("买入金额高于最高买入值 accountId{}, amount:{}", buyRecord.getAccountId(), buyRecord.getAmount());
            return false;
        }
        if (buyRecordBusiness.hasBuy(buyRecord.getAccountId(), buyRecord.getStrategyId(), coin.getCoin(), buyRecord.getTimes()) == true) {
            log.warn("买入失败-该策略已买 account:{} coin:{}", buyRecord.getAccountId(), coin.getCoin());
            return false;
        }
        return true;
    }

    public boolean checkSell(OkxSellRecord sellRecord, OkxCoin coin, TradeDto tradeDto) {
        if (sellRecord.getQuantity().compareTo(coin.getCount()) >= 0) {
            if (coin.getCount().compareTo(BigDecimal.ZERO) > 0 && coin.getCount().compareTo(coin.getUnit()) > 0) {
//                sellRecord.setQuantity(coin.getCount());
//                tradeDto.setSz(coin.getCount());
                log.warn("卖出数据变更:accountId:{}, coin:{}, count:{}", sellRecord.getAccountId(), coin.getCoin(), coin.getCount() );
            } else {
                log.error("卖出失败-余额不足accountId:{}, coin:{}, sellQuantity:{},coinQuantity:{}", sellRecord.getAccountId(), coin.getCoin(), sellRecord.getQuantity(), coin.getCount() );
                return false;
            }
        }
        OkxBuyRecord buyRecord = buyRecordBusiness.getById(sellRecord.getBuyRecordId());
        if (sellRecord.getPrice().compareTo(buyRecord.getPrice().add(buyRecord.getPrice().multiply(new BigDecimal(settingService.selectSettingByKey(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL))))) < 0
            && tradeDto.getOrdType().equals(OkxOrdTypeEnum.LIMIT.getValue())) {
            log.warn("卖出失败-未涨1%{}, coin:{},buyPrice:{}, sellPrice:{}", sellRecord.getAccountId(), sellRecord.getCoin(), buyRecord.getPrice(), sellRecord.getPrice());
            return false;
        }
        return true;
    }

    public String checkKeyUnique(OkxStrategy strategy){
        OkxStrategy dbStrategy = this.getById(strategy.getId());
        if (dbStrategy != null && dbStrategy.getStrategyName().equals(strategy.getStrategyName())){
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    public List<OkxSetting> listByStrategyId(Integer strategyId) {
        String IdsStr = list(new OkxStrategy(strategyId,null,null,null)).get(0).getSettingIds();
        //转换long类型的数组
        Long[] IdsArr =  DtoUtils.StringToLong(IdsStr.split(","));
        return settingService.selectSettingByIds(IdsArr);
    }

    public List<OkxSetting> listByAccountId(Integer accountId) {
        String IdsStr = list(new OkxStrategy(null,accountId,null,null)).get(0).getSettingIds();
        //转换long类型的数组
        Long[] IdsArr =  DtoUtils.StringToLong(IdsStr.split(","));
        return settingService.selectSettingByIds(IdsArr);
    }

    public ModeTypeEnum getModeType(Integer accountId) {
        OkxSetting setting = listByAccountId(accountId).stream().filter(item -> item.getSettingKey().equals("mode_type")).findFirst().get();
        return ModeTypeEnum.getModeType(setting.getSettingValue());

    }

    public boolean isGrid(Integer accountId) {
        List<OkxSetting> list = listByAccountId(accountId).stream().filter(item -> item.getSettingValue().equals(ModeTypeEnum.GRID.getValue())).collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(list) ;
    }

    public boolean isMarket(Integer accountId) {
        List<OkxSetting> list = listByAccountId(accountId).stream().filter(item -> item.getSettingValue().equals(ModeTypeEnum.MARKET.getValue())).collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(list) ;
    }
}
