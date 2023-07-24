package com.seektop.fund.service;

import java.util.Date;
import java.util.List;

public interface FundDataFixService {

    /**
     * 指定充值订单的充值记录重新同步
     *
     * @param orderIdList
     */
    void rechargeReSynchronize(List<String> orderIdList);

    /**
     * 指定提现订单的提现记录重新同步
     *
     * @param orderIdList
     */
    void withdrawReSynchronize(List<String> orderIdList);

    /**
     * 指定时间段内的充值记录重新同步
     *
     * @param startDate
     * @param endDate
     */
    void rechargeReSynchronize(Date startDate, Date endDate);

}