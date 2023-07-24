package com.seektop.common.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author Jesse
 * @Date 2019/8/13 19:31
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlRequestHeader implements Serializable {

    private static final long serialVersionUID = 1011198069464556159L;
    /**
     *   行为
     *  参见GlActionEnum
     */
    private String action;
    /**
     *  渠道ID
     */
    private String channelId;
    /**
     * 渠道名称
     */
    private String channelName;
    /**
     *  用户id
     */
    private String userId;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 转账单号
     */
    private String tradeId;
    /**
     *  终端类型：  PC、H5、安卓、IOS、PAD
     */
    private String terminal;
    /**
     *  体育项目
     */
    private String SportId;
    /**
     *  指出“早盘”、“今日”或者“滚球”的盘口
     * 1 = 早盘
     * 2 = 今日
     * 3 = 滚球
     */
    private String Market;
}
