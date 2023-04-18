package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.enums.ModeTypeEnum;
import com.ruoyi.okx.enums.OkxOrdTypeEnum;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.service.SettingService;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Component
public class StrategyBusiness  {
    private static final Logger log = LoggerFactory.getLogger(StrategyBusiness.class);

    @Resource
    private SettingService settingService;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private CoinBusiness coinBusiness;


    public boolean checkBuy(OkxBuyRecord buyRecord, OkxCoin coin, List<OkxSetting> settingList) {

        if (coin.getStatus() != CoinStatusEnum.OPEN.getStatus()) {
            return false;
        }
        if (coin.getUnit().compareTo(BigDecimal.ZERO) <= 0 || ObjectUtils.isEmpty(coin.getCount())) {
            log.warn("买入校验策略-单位为0 coin:{}", JSON.toJSONString(coin));
            return false;
        }

        //现有数量与买入数量不能超过最高手持数量
        BigDecimal totalTimes = coin.getCount().add(buyRecord.getQuantity()).divide(coin.getUnit());
        BigDecimal buyMaxTime = new BigDecimal(settingList.stream()
                .filter(item -> item.getSettingKey().equalsIgnoreCase(OkxConstants.BUY_MAX_TIMES)).findFirst().get().getSettingValue());
        if (totalTimes.compareTo(buyMaxTime) > 0) {
            log.warn("不能高于最高手持倍数 coin:{},count:{}", coin.getCoin());
            return false;
        }

        if (buyRecord.getAmount().compareTo(new BigDecimal(settingList.stream()
                        .filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MAX_USDT)).findFirst().get().getSettingValue())) > 0) {
            log.warn("买入金额高于最高买入值 accountId{}, amount:{}", buyRecord.getAccountId(), buyRecord.getAmount());
            return false;
        }

        String modeType = settingList.stream()
                .filter(item -> item.getSettingKey().equalsIgnoreCase(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
        if (modeType.equalsIgnoreCase(ModeTypeEnum.GRID.getValue())
            && buyRecordBusiness.hasBuy(buyRecord.getAccountId(), buyRecord.getStrategyId(), coin.getCoin(), modeType) == true) {
            //log.warn("grid  has buy this coin:{},strategy:{}", buyRecord.getCoin(), buyRecord.getStrategyId());
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
            return false;
        }
        return true;
    }

}
