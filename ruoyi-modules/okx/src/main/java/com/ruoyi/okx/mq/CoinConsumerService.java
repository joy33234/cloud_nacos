package com.ruoyi.okx.mq;

import com.alibaba.fastjson.JSON;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.rabbitmq.AbsConsumerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
@Slf4j
public class CoinConsumerService extends AbsConsumerService {

    @Override
    public void  onConsumer(Object data) throws IOException {
        OkxCoin coin = (OkxCoin) data;
        coin.setBalance(BigDecimal.TEN);
        log.info("CoinConsumerService: {}", JSON.toJSONString(coin));
    }



}
