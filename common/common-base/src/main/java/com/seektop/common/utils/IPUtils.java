package com.seektop.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Random;

public class IPUtils {

    private final static int[][] range = {
            {607649792, 608174079},
            {1038614528, 1039007743},
            {1783627776, 1784676351},
            {2035023872, 2035154943},
            {2078801920, 2079064063},
            {-1950089216, -1948778497},
            {-1425539072, -1425014785},
            {-1236271104, -1235419137},
            {-770113536, -768606209},
            {-569376768, -564133889}
    };

    /**
     * 获取IP前三段
     *
     * @param ip
     * @return
     */
    public static String getSubIp(String ip) {
        int count = StringUtils.countMatches(ip, ".");
        if (count == 3) {
            //获取ip前三段
            ip = StringUtils.substring(ip, 0, StringUtils.ordinalIndexOf(ip, ".", 3) + 1);
        }
        return ip;
    }

    /**
     * 随机生成一个IP地址
     *
     * @return
     */
    public static String getRandomIp() {
        Random random = new Random();
        int index = random.nextInt(10);
        return num2Ip(range[index][0] + new Random().nextInt(range[index][1] - range[index][0]));
    }

    protected static String num2Ip(int ip) {
        int[] b = new int[4];
        b[0] = (int) ((ip >> 24) & 0xff);
        b[1] = (int) ((ip >> 16) & 0xff);
        b[2] = (int) ((ip >> 8) & 0xff);
        b[3] = (int) (ip & 0xff);
        return Integer.toString(b[0]) + "." + Integer.toString(b[1]) + "." + Integer.toString(b[2]) + "." + Integer.toString(b[3]);
    }

    public static void main(String[] args) {
        System.out.println(IPUtils.getRandomIp());
    }

}