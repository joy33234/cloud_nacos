package com.seektop.common.netease;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.seektop.common.http.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class NECaptchaVerifier {

    /**
     * 版本号
     */
    private String version;

    /**
     * 验证码ID
     */
    private String captchaId;

    /**
     * 验证API地址
     */
    private String verifyApi;

    /**
     * 密钥对
     */
    private NESecretPair secretPair;

    @Autowired
    private OkHttpUtil okHttpUtil;

    /**
     * 初始化参数
     *
     * @param captchaId
     * @param version
     * @param verifyApi
     * @param secretPair
     */
    public void init(String captchaId, String version, String verifyApi, NESecretPair secretPair) {
        this.captchaId = captchaId;
        this.version = version;
        this.verifyApi = verifyApi;
        this.secretPair = secretPair;
    }

    public VerifyResult verify(String validate, String user) {
        if (!StringUtils.hasText(validate)) {
            return VerifyResult.fakeNormalResult("validate data is empty");
        }
        user = (user == null) ? "" : user;
        Map<String, String> params = Maps.newHashMap();
        params.put("captchaId", captchaId);
        params.put("validate", validate);
        params.put("user", user);
        // 公共参数
        params.put("secretId", secretPair.secretId);
        params.put("version", version);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("nonce", String.valueOf(ThreadLocalRandom.current().nextInt()));
        // 计算请求参数签名信息
        String signature = sign(secretPair.secretKey, params);
        params.put("signature", signature);
        String resp = okHttpUtil.post(verifyApi, params);
        log.info("resp = {}", resp);
        return verifyRet(resp);
    }

    public static String sign(String secretKey, Map<String, String> params) {
        String[] keys = params.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        StringBuffer sb = new StringBuffer();
        for (String key : keys) {
            sb.append(key).append(params.get(key));
        }
        sb.append(secretKey);
        try {
            return DigestUtils.md5DigestAsHex(sb.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private VerifyResult verifyRet(String resp) {
        if (!StringUtils.hasText(resp)) {
            return VerifyResult.fakeNormalResult(resp);
        }
        try {
            VerifyResult verifyResult = JSONObject.parseObject(resp, VerifyResult.class);
            return verifyResult;
        } catch (Exception e) {
            return VerifyResult.fakeNormalResult(resp);
        }
    }

}