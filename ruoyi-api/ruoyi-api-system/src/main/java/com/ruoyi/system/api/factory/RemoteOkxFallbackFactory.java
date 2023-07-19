package com.ruoyi.system.api.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.system.api.RemoteOkxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级处理
 * 
 * @author ruoyi
 */
@Component
public class RemoteOkxFallbackFactory implements FallbackFactory<RemoteOkxService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteOkxFallbackFactory.class);

    @Override
    public RemoteOkxService create(Throwable throwable)
    {
        log.error("okx服务调用失败:{}", throwable.getMessage());
        return new RemoteOkxService()
        {

            @Override
            public R<Void> syncCurrencies(String source)
            {
                return R.fail("同步帐户币种数量失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncTicker(String source)
            {
                return R.fail("同步币种信息数量失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncTickerDb(String source)
            {
                return R.fail("保存币种行情数据到Db失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncCount(String source)
            {
                return R.fail("同步帐户币种数量失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncBuyOrder(String source)
            {
                return R.fail("同步订单信息失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncBuyOrderFee(String source)
            {
                return R.fail("同步订单手续费信息失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncSellOrder(String source)
            {
                return R.fail("同步订单信息失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncCoin(String source)
            {
                return R.fail("同步coin标准值失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> syncCoinTurnOver(String source)
            {
                return R.fail("同步syncCoinTurnOver失败:" + throwable.getMessage());
            }

            @Override
            public R<Void> init(String source)
            {
                return R.fail("init失败:" + throwable.getMessage());
            }
        };
    }
}
