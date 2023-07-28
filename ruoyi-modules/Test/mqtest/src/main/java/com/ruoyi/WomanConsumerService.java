package com.ruoyi;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.rabbitmq.AbsConsumerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class WomanConsumerService extends AbsConsumerService {

    @Override
    public  void  onConsumer(String data) throws IOException {
        User user = JSON.parseObject(data, User.class);
        log.info("WomantConsumerService1--1 - user-name{}", user.getName());
        ack();
    }

}
