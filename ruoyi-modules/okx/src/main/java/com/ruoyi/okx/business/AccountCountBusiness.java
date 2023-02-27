package com.ruoyi.okx.business;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.okx.domain.OkxAccountCount;
import com.ruoyi.okx.mapper.OkxAccountCountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AccountCountBusiness extends ServiceImpl<OkxAccountCountMapper, OkxAccountCount> {
    private static final Logger log = LoggerFactory.getLogger(AccountCountBusiness.class);

    @Resource
    private OkxAccountCountMapper mapper;



}
