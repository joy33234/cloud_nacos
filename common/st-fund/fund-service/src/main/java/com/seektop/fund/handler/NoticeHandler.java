package com.seektop.fund.handler;

import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;

public interface NoticeHandler {

    /**
     * 成功通知
     * @param successDto
     */
    void doSuccessNotice(NoticeSuccessDto successDto);

    /**
     * 失败通知
     * @param failDto
     */
    void doFailNotice(NoticeFailDto failDto);
}
