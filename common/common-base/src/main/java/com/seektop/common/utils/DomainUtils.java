package com.seektop.common.utils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DomainUtils {

    private static  String topDomainRegex = "[^\\.]+(\\.com\\.cn|" +
            "\\.net\\.cn|" +
            "\\.org\\.cn|" +
            "\\.gov\\.cn|" +
            "\\.com|" +
            "\\.ph|"+
            "\\.net|" +
            "\\.cn|" +
            "\\.org|" +
            "\\.cc|" +
            "\\.me|" +
            "\\.tel|" +
            "\\.mobi|" +
            "\\.asia|" +
            "\\.biz|" +
            "\\.info|" +
            "\\.name|" +
            "\\.tv|" +
            "\\.hk|" +
            "\\.公司|" +
            "\\.中国|" +
            "\\.网络)";
    /**
     * 获取url的顶级域名
     *
     * @param
     * @return
     */
    public static String getTopDomain(String url) {
        try {
            //获取值转换为小写
            if(url.startsWith("http")) {
                url = new URL(url).getHost();//news.hexun.com
            }
            Pattern pattern = Pattern.compile(topDomainRegex);
            Matcher matcher = pattern.matcher(url.toLowerCase());
            while (matcher.find()) {
                return matcher.group();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static String getDomain(String url) {
        try {
            //获取值转换为小写
            if(url.startsWith("http")) {
                url = new URL(url).getHost();//news.hexun.com
            }
            Pattern pattern = Pattern.compile(topDomainRegex);
            Matcher matcher = pattern.matcher(url.toLowerCase());
            while (matcher.find()) {
                return url;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

}