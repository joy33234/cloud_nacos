package com.seektop.common.redis.push;

import com.seektop.common.redis.RedisService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class GlPushClient {

    @Resource
    private RedisService redisService;

    // prefix
    private static final String prefix = "push:channel:";

    // BTI的 赛事 权重订阅通知频道
    private static final String CH_BTI_MATCH_WEIGHT = "proxyd:ch:bti:match:weight";

    // BTI的 联赛 权重订阅通知频道
    private static final String CH_BTI_LEAGUE_WEIGHT = "proxyd:ch:bti:league:weight";

    // publish msg
    public void send(Channel channel, Integer userId, Object data) {
        String msg = GlMsgPack.pack(channel, data);
        String channelName = prefix + channel.value();
        if (userId != null) {
            channelName += ":" + userId;
        }
        redisService.publish(channelName, msg);
    }

    public void sendBtiMatch() {
        redisService.publish(CH_BTI_MATCH_WEIGHT, "message");
    }

    public void sendBtiLeague() {
        redisService.publish(CH_BTI_LEAGUE_WEIGHT, "message");
    }
}
