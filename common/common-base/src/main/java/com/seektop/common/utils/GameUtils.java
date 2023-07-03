package com.seektop.common.utils;


import com.seektop.constant.game.GameChannelConstants;

public class GameUtils {

    /**
     * 处理三方游戏账户名
     *
     * @param merUsername
     * @param channelId
     * @return
     */
    public static String getMerUsername(String merUsername, Integer channelId) {
        // AG和eBET需要特殊处理三方用户名
        if (channelId == GameChannelConstants.CHANNEL_AG || channelId == GameChannelConstants.CHANNEL_EBET) {
            merUsername = merUsername.replaceFirst("wxg", "");
        }
        // 如果是PT游戏需要特殊处理三方用户名
        if (channelId == GameChannelConstants.CHANNEL_IM_PT) {
            merUsername = merUsername.replaceFirst("IM06X_IM06X", "");
        }
        // 如果是LB彩票需要特殊处理三方用户名
        if (channelId == GameChannelConstants.CHANNEL_LB) {
            merUsername = merUsername.replaceFirst("wlb", "");
        }
        // 如果是双赢彩票需要特殊处理三方用户名
        if (channelId == GameChannelConstants.CHANNEL_IM_SW) {
            merUsername = merUsername.replaceFirst("IM06X", "");
        }
        return merUsername;
    }

}