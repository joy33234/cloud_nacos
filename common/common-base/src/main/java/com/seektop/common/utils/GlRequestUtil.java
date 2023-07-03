package com.seektop.common.utils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ken on 2018/5/14.
 */
public class GlRequestUtil {
    public static final int WEMART_LOGIN_TOKEN_EXPIRES = 60 * 60 * 24 * 90;
    public static final int WEMART_ADMIN_LOGIN_TOKEN_EXPIRES = -1;
    public static final String WEMART_DOMAIN = ".betball.cn";

    public static String getCookie(HttpServletRequest request, String name) {
        if (name == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                Cookie cookie = cookies[i];
                if (cookie == null)
                    continue;
                boolean match = name.equals(cookie.getName());
                if (match) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public static void addCookie(HttpServletResponse response, String strname, String value) {
        Cookie cookie = new Cookie(strname, value);
        cookie.setMaxAge(WEMART_LOGIN_TOKEN_EXPIRES);// 60 * 60 * 24 * 90
        cookie.setPath("/");
        cookie.setDomain(WEMART_DOMAIN);
        response.addCookie(cookie);
    }

    /**
     * 删除COOKIE
     *
     * @param request
     * @param response
     * @param name
     */
    public static void clearCookie(HttpServletRequest request, HttpServletResponse response,
                                   String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                Cookie cookie = cookies[i];
                if (name.equals(cookie.getName())) {
                    cookie.setValue("");
                    cookie.setDomain(WEMART_DOMAIN);
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    response.addCookie(cookie);
                }

            }
        }
    }

    public static String inputStream2String(InputStream in) throws IOException {
        StringBuffer out = new StringBuffer();
        try {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) {
                out.append(new String(b, 0, n));
            }

            return out.toString();
        } catch (IOException ex) {
            throw ex;
        }

    }

    public static String getDomain(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        url = url.split("://")[1];
        url= url.split("/")[0];
        return url;
    }

}
