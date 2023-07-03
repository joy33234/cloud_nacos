package com.seektop.common.netease;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.seektop.common.http.OkHttpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class NEDeviceNumber {

    private final OkHttpUtil okHttpUtil;

    public JSONObject getDeviceId(NEDeviceNumberParamDO paramDO) {
        try {
            Map<String, String> params = Maps.newHashMap();
            params.put("version", "300");
            params.put("secretId", paramDO.getSecretId());
            params.put("businessId", paramDO.getBusinessId());
            params.put("timestamp", System.currentTimeMillis() / 1000 + "");
            params.put("nonce", Math.random() + "");
            params.put("token", paramDO.getToken());
            // 获取签名字符串
            String sign = genSignature(paramDO.getSecretKey(), params);
            if (StringUtils.isEmpty(sign)) {
                log.error("获取到的签名字符串是空，无法请求");
                return null;
            }
            params.put("signature", sign);
            for (String key : params.keySet()) {
                log.debug("参数{}的值是{}", key, params.get(key));
            }
            // 获取请求结果
            String response = okHttpUtil.post(paramDO.getApi(), params);
            if (StringUtils.isEmpty(response)) {
                log.error("易盾请求设备号返回的结果是空的");
                return null;
            }
            log.info("易盾请求的设备号结果是{}", response);

            JSONObject responseObj = JSON.parseObject(response);
            if (ObjectUtils.isEmpty(responseObj)) {
                return null;
            }
            if (responseObj.containsKey("code") == false || responseObj.getIntValue("code") != 200) {
                return null;
            }
            if (responseObj.containsKey("result") == false) {
                return null;
            }
            JSONObject resultObj = responseObj.getJSONObject("result");
            if (resultObj.containsKey("detail") == false) {
                return null;
            }
            return resultObj.getJSONObject("detail").getJSONObject("deviceData");
        } catch (Exception ex) {
            log.error("易盾请求设备号发生异常", ex);
            return null;
        }
    }

    public String genSignature(final String secretKey, final Map<String, String> params) {
        if (StringUtils.isEmpty(secretKey) || CollectionUtils.isEmpty(params)) {
            return "";
        }
        try {
            // 1. 参数名按照ASCII码表升序排序
            String[] keys = params.keySet().toArray(new String[0]);
            Arrays.sort(keys);
            // 2. 按照排序拼接参数名与参数值
            StringBuffer paramBuffer = new StringBuffer();
            for (String key : keys) {
                paramBuffer.append(key).append(params.get(key) == null ? "" : params.get(key));
            }
            // 3. 将secretKey拼接到最后
            paramBuffer.append(secretKey);
            // 4. MD5是128位长度的摘要算法，用16进制表示，一个十六进制的字符能表示4个位，所以签名后的字符串长度固定为32个十六进制字符。
            return DigestUtils.md5Hex(paramBuffer.toString().getBytes("UTF-8"));
        } catch (Exception ex) {
            log.error("易盾请求设备号进行签名字符串时发生异常", ex);
            return "";
        }
    }

}