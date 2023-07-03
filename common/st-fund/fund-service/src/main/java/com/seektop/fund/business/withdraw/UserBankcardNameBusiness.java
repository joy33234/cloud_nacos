package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.UserBankcardNameMapper;
import com.seektop.fund.model.UserBankcardName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class UserBankcardNameBusiness extends AbstractBusiness<UserBankcardName> {

    private final UserBankcardNameMapper userBankcardNameMapper;

}