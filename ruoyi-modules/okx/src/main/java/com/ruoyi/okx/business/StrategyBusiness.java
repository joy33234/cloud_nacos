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


    public boolean checkBuy(OkxBuyRecord buyRecord, OkxCoin coin, List<OkxSetting> settingList,BigDecimal totalBuyAmount) {

        if (coin.getStatus() != CoinStatusEnum.OPEN.getStatus()) {
            return false;
        }
        if (coin.getUnit().compareTo(BigDecimal.ZERO) <= 0 || ObjectUtils.isEmpty(coin.getStandard())) {
            log.warn("买入校验策略-单位为0 coin:{}", JSON.toJSONString(coin));
            return false;
        }

        //现有数量与买入数量不能超过最高手持数量
//        BigDecimal totalTimes = coin.getCount().add(buyRecord.getQuantity()).divide(coin.getUnit());
//        BigDecimal buyMaxTime = new BigDecimal(settingList.stream()
//                .filter(item -> item.getSettingKey().equalsIgnoreCase(OkxConstants.BUY_MAX_TIMES)).findFirst().get().getSettingValue());
//        if (totalTimes.compareTo(buyMaxTime) > 0) {
//            log.warn("不能高于最高手持倍数 coin:{},count:{}", coin.getCoin());
//            return false;
//        }  改为每种币买入相同数量U


        if (buyRecord.getAmount().compareTo(new BigDecimal(settingList.stream()
                        .filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MAX_USDT)).findFirst().get().getSettingValue())) > 0) {
            log.warn("买入金额高于最高买入值 accountId{}, amount:{}", buyRecord.getAccountId(), buyRecord.getAmount());
            return false;
        }

        BigDecimal totalUSDT = totalBuyAmount.add(buyRecord.getAmount());
        BigDecimal buySumMaxUsdt = new BigDecimal(settingList.stream()
                .filter(item -> item.getSettingKey().equalsIgnoreCase(OkxConstants.BUY_SUM_MAX_USDT)).findFirst().get().getSettingValue());
        if (totalUSDT.compareTo(buySumMaxUsdt) > 0) {
            log.warn("订单总额不能高于最高买入USDT值 coin:{},count:{}", coin.getCoin(),totalUSDT);
            return false;
        }

        String modeType = settingList.stream()
                .filter(item -> item.getSettingKey().equalsIgnoreCase(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
        if (modeType.equalsIgnoreCase(ModeTypeEnum.GRID.getValue())
            && buyRecordBusiness.hasBuy(buyRecord.getAccountId(), buyRecord.getStrategyId(), coin.getCoin(), modeType) == true) {
            if (coin.getCoin().equalsIgnoreCase("BTC")) {
                log.warn("grid  has buy this coin:{},strategy:{}", buyRecord.getCoin(), buyRecord.getStrategyId());
            }
            return false;
        }
        return true;
    }

    public boolean checkSell(OkxSellRecord sellRecord, OkxCoin coin, TradeDto tradeDto) {
        if (sellRecord.getQuantity().compareTo(coin.getBalance()) > 0) {
            log.warn("卖出失败-余额不足 sellRecord:{}, coin:{}, sellQuantity:{},tradeDto:{}", JSON.toJSONString(sellRecord), JSON.toJSONString(coin),JSON.toJSONString(tradeDto) );
            if (coin.getBalance().compareTo(coin.getUnit()) > 0) {
                sellRecord.setQuantity(coin.getBalance());
                tradeDto.setSz(coin.getBalance());
                log.warn("卖出失败-余额不足 修正卖出数量 accountId:{}, coin:{}, sellQuantity:{}", sellRecord.getAccountId(), coin.getCoin(), sellRecord.getQuantity());
            }
        }

        //TODO test
//        OkxBuyRecord buyRecord = buyRecordBusiness.getById(sellRecord.getBuyRecordId());
//        if (sellRecord.getPrice().compareTo(buyRecord.getPrice().add(buyRecord.getPrice().multiply(new BigDecimal(settingService.selectSettingByKey(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL))))) < 0
//            && tradeDto.getOrdType().equals(OkxOrdTypeEnum.LIMIT.getValue())) {
//            log.warn("limit low min Percent coin:{}",  coin.getCoin());
//            return false;
//        }
        return true;
    }

}
