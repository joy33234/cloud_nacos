package com.seektop.common.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UrlUtil {

    private static final UrlUtil instance = new UrlUtil();

    private UrlUtil() {

    }

    public static UrlUtil getInstance() {
        return instance;
    }

    /**
     * 获取URL中的参数
     *
     * @param url
     * @return
     */
    public Map<String, String> getParam(final String url) {
        Map<String, String> params = new ConcurrentHashMap<>();
        try {
            URL urlObj = new URL(url);
            String query = urlObj.getQuery();
            String[] datas = query.split("\\&");
            for (int i = 0, len = datas.length; i < len; i++) {
                if (StringUtils.isEmpty(datas[i])) {
                    continue;
                }
                String[] paramArray = datas[i].split("\\=");
                if (paramArray == null || paramArray.length < 2) {
                    continue;
                }
                params.put(paramArray[0], paramArray[1]);
            }
        } catch (Exception e) {
            log.error("UrlUtil.getParam", e);
        }
        return params;
    }

}
