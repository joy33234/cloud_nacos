package com.ruoyi.common.core.utils;

import java.util.Date;
import java.util.UUID;

public class TokenUtil {
    public static String getToken() {
        return MD5.toHex(uuid());
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String getOkxOrderId(Date now) {
        if (now == null)
            now = new Date();
        return "JLOKX" + DateUtil.getFormateDate(now, "yyyyMMddHHmmss") + uuid().substring(0, 6);
    }
}
