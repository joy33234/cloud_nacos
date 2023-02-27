//package com.ruoyi.okx.business;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import java.math.BigDecimal;
//import java.util.List;
//import javax.annotation.Resource;
//
//import com.ruoyi.common.core.constant.UserConstants;
//import com.ruoyi.common.core.utils.StringUtils;
//import com.ruoyi.okx.domain.*;
//import com.ruoyi.okx.mapper.SellStrategyMapper;
//import com.ruoyi.okx.params.DO.OkxAccountDO;
//import com.ruoyi.okx.params.dto.TradeDto;
//import com.ruoyi.okx.utils.Constant;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//@Component
//public class SellStrategyBusiness extends ServiceImpl<SellStrategyMapper, OkxSellStrategy> {
//    private static final Logger log = LoggerFactory.getLogger(SellStrategyBusiness.class);
//
//    @Resource
//    private BuyRecordBusiness buyRecordBusiness;
//
//    @Resource
//    private SellStrategyMapper strategyMapper;
//
//    public List<OkxSellStrategy> list(OkxSellStrategy strategy) {
//        LambdaQueryWrapper<OkxSellStrategy> wrapper = new LambdaQueryWrapper();
////        wrapper.eq((null != buyRecordDO.getCoin()), OkxAccount::getCoin, buyRecordDO.getCoin());
////        wrapper.between((account.getCreateTime() != null), OkxAccount::getUpdateTime, account.getCreateTime(), account.getEndTime());
//        return strategyMapper.selectList(wrapper);
//    }
//
//    public boolean check(OkxSellRecord sellRecord, OkxCoin coin, TradeDto tradeDto) {
//        if (sellRecord.getQuantity().compareTo(coin.getCount()) > 0)
//            if (coin.getCount().compareTo(BigDecimal.ZERO) > 0 && coin.getCount().compareTo(coin.getUnit()) > 0) {
//                sellRecord.setQuantity(coin.getCount());
//                tradeDto.setSz(coin.getCount());
//                log.warn("卖出数据变更:accountId:{}, coin:{}, count:{}", new Object[] { sellRecord.getAccountId(), coin.getCoin(), coin.getCount() });
//            } else {
//                log.error("卖出失败-余额不足accountId:{}, coin:{}, sellQuantity:{},coinQuantity:{}", new Object[] { sellRecord.getAccountId(), coin.getCoin(), sellRecord.getQuantity(), coin.getCount() });
//                return false;
//            }
//        OkxBuyRecord buyRecord = this.buyRecordBusiness.getById(sellRecord.getBuyRecordId());
//        if (sellRecord.getPrice().compareTo(buyRecord.getPrice().add(buyRecord.getPrice().multiply(Constant.TRADE_ONE_PERCENT))) < 0) {
//            log.warn("卖出失败-未涨1%{}, coin:{},buyPrice:{}, sellPrice:{}", new Object[] { sellRecord.getAccountId(), sellRecord.getCoin(), buyRecord.getPrice(), sellRecord.getPrice() });
//            return false;
//        }
//        return true;
//    }
//
//    public String checkKeyUnique(OkxSellStrategy strategy)
//    {
//        OkxSellStrategy dbStrategy = this.getById(strategy.getId());
//        if (StringUtils.isNotNull(dbStrategy) && dbStrategy.getName().equals(strategy.getName()))
//        {
//            return UserConstants.NOT_UNIQUE;
//        }
//        return UserConstants.UNIQUE;
//    }
//}
