package com.ruoyi.file.config;

import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;

public class AfterConfigListener implements SmartApplicationListener, Ordered {


    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> aClass) {
        return(ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(aClass) ||
                ApplicationPreparedEvent.class.isAssignableFrom(aClass) );
    }
    @Override
    public int getOrder() {
        return(ConfigFileApplicationListener.DEFAULT_ORDER + 1);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        RestTemplate restTemplate=new RestTemplate();
        if (applicationEvent instanceof ApplicationEnvironmentPreparedEvent)
        {
            System.setProperty("spring.cloud.nacos.discovery.ip", "119.91.74.48");
        }
    }
}

