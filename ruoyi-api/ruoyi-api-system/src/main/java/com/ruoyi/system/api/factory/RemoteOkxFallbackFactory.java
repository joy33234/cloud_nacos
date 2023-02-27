package com.ruoyi.system.api.factory;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.system.api.RemoteOkxService;
import com.ruoyi.system.api.domain.SysUser;
import com.ruoyi.system.api.model.LoginUser;
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
            public R<Boolean> syncCurrencies(String source)
            {
                return R.fail("同步帐户币种数量失败:" + throwable.getMessage());
            }

            @Override
            public R<Boolean> syncTicker(String source)
            {
                return R.fail("同步币种信息数量失败:" + throwable.getMessage());
            }


            @Override
            public R<Boolean> syncMin(String source)
            {
                return R.fail("同步帐户币种数量失败:" + throwable.getMessage());
            }


            @Override
            public R<Boolean> syncCount(String source)
            {
                return R.fail("同步帐户币种数量失败:" + throwable.getMessage());
            }

            @Override
            public R<Boolean> syncBuyOrder(String source)
            {
                return R.fail("同步订单信息失败:" + throwable.getMessage());
            }


            @Override
            public R<Boolean> syncSellOrder(String source)
            {
                return R.fail("同步订单信息失败:" + throwable.getMessage());
            }
        };
    }
}
