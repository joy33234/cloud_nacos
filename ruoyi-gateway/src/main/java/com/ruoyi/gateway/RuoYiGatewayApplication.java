package com.ruoyi.gateway;

import com.ruoyi.gateway.config.AfterConfigListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 网关启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
public class RuoYiGatewayApplication
{
    public static void main(String[] args)
    {
        SpringApplication springApplication = new SpringApplication(RuoYiGatewayApplication.class);
        springApplication.addListeners(new AfterConfigListener());
        springApplication.run(args);

        System.out.println("(♥◠‿◠)ﾉﾞ  若依网关启动成功   ლ(´ڡ`ლ)ﾞ " );
    }
}
