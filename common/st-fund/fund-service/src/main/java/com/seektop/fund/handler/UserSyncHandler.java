package com.seektop.fund.handler;

import java.math.BigDecimal;

public interface UserSyncHandler {

    void userBalanceSync(Integer userId, String coin, BigDecimal balance);

}
