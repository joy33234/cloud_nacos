package com.seektop.fund.service;

import java.util.Date;

public interface RechargeDataBackUpService {

    /**
     * 查询历史记录表最后创建实际时间
     *
     * @return
     */
    public String getMaxRechargeDate(String shard);

    /**
     * 备份数据到History表
     */
    public void backUp(Date backDate);

    /**
     * 清除Recharge数据
     *
     * @return
     */
    public Integer clean();

    /**
     * 验证History表是否存在
     *
     * @param shard
     * @return
     */
    public boolean checkTable(String shard);
}
