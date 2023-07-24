package com.seektop.common.utils;

import java.util.UUID;

/**
 * Created by ken on 2018/3/16.
 */
public class ProjectTokenUtils {

    public static String genToken() {
        //使用uuid作为源token
        String token = UUID.randomUUID().toString().replace("-", "");

        return token;
    }
}
