package com.seektop.fund.service;

import com.seektop.exception.GlobalException;

import java.util.Date;

public interface GlRechargeErrorService {

    /**
     * 充值异常记录保存7天，过期清除
     *
     * @param date
     * @throws GlobalException
     */
    void deleteRecord(Date date) throws GlobalException;


}