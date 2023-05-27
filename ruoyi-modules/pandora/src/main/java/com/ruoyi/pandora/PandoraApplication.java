package com.ruoyi.pandora;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import com.ruoyi.common.swagger.annotation.EnableCustomSwagger2;
import com.ruoyi.pandora.config.AfterConfigListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 系统模块
 * 
 * @author ruoyi
 */
@EnableCustomConfig
@EnableCustomSwagger2
@EnableRyFeignClients
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PandoraApplication
{
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(PandoraApplication.class);
        springApplication.addListeners(new AfterConfigListener());
        springApplication.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  pandora 系统模块启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
