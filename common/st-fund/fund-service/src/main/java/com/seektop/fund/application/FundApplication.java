package com.seektop.fund.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;

@Slf4j
@EnableDubbo(scanBasePackages = {"com.seektop.fund.service"})
@MapperScan(value = {"com.seektop.fund.mapper","com.seektop.digital.mapper"})
@SpringBootApplication(scanBasePackages = {"com.seektop.common", "com.seektop.fund"})
public class FundApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundApplication.class, args);
    }

}