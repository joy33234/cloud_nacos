package com.seektop.fund.handler.event;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.redis.RedisService;
import com.seektop.common.utils.MD5;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeManageBusiness;
import com.seektop.fund.business.recharge.GlRechargePayBusiness;
import com.seektop.fund.controller.backend.result.recharge.RechargePayResult;
import com.seektop.fund.model.GlRechargePay;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 代客充值回调通知
 */
@Slf4j
@Component
public class OrderNotifyEventListener {

    @Resource
    private GlRechargePayBusiness rechargePayBusiness;
    @Autowired
    private GlRechargeManageBusiness rechargeManageBusiness;
    @Autowired
    private OkHttpUtil okHttpUtil;
    @Autowired
    private RedisService redisService;
    @Value("${order.notify.secret.key:''}")
    private String secretKey;

    @Async
    @EventListener
    public void onOrderNotifyEvent(OrderNotifyEvent event) {
        log.info("order_notify orderId:{}", event.getSource());
        try {
            String orderId = (String) event.getSource();
            sendNotify(orderId);
        }
        catch (Exception e) {
            log.error("代客充值回调接口异常", e);
        }
    }

    /**
     * 代客充值回调
     *
     * @param orderId
     * @throws GlobalException
     */
    private void sendNotify(String orderId) throws GlobalException {
        // 查询出回调地址，回调接口
        String key = String.format(RedisKeyHelper.RECHARGE_INSTEAD_NOTIFY_URL, orderId);
        OrderNotifyDto notifyDto = redisService.get(key, OrderNotifyDto.class);
        if (ObjectUtils.isEmpty(notifyDto)) {
            log.info("order_notify url is empty");
            return;
        }
        String url = notifyDto.getUrl();
        RechargePayResult payResult = rechargeManageBusiness.queryRechargeOrder(orderId);
        Optional<GlRechargePay> optPay = Optional.ofNullable(rechargePayBusiness.findById(orderId));
        Map<String, String> params = new HashMap<>();
        params.put("orderId", orderId);
        params.put("status", payResult.getStatus().toString());
        optPay.ifPresent(pay -> {
            params.put("amount", String.valueOf(pay.getAmount()));
            params.put("fee", String.valueOf(pay.getFee()));
            params.put("payTime", String.valueOf(pay.getPayDate().getTime()));
        });
        String sign = getSign(params); // 签名
        params.put("sign", sign);
        log.info("order_notify request url：{}, params：{}", url, params);
        String response = okHttpUtil.post(url, params);
        log.info("order_notify response：{}", response);
        JSONObject jsonObject = JSONObject.parseObject(response);
        if (null != jsonObject && 1 == jsonObject.getInteger("code")) {
            redisService.delete(key);
        }
    }

    private String getSign(Map<String, String> params) {
        // 1. 参数名按照ASCII码表升序排序
        String[] keys = params.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        // 2. 按照排序拼接参数名与参数值
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (StringUtils.isNotBlank(value)) {
                sb.append(key).append("=").append(value).append("&");
            }
        }
        // 3. 将secretKey拼接到最后
        sb.append(secretKey);
        // 4. MD5
        return MD5.md5(sb.toString());
    }
}
