package com.seektop.common.utils;

import com.google.common.collect.Sets;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验器：利用正则表达式校验邮箱、手机号等
 */
public class RegexValidator {

    /**
     * 正则表达式：验证用户名
     */
    public static final String REGEX_USERNAME = "^[a-zA-Z][a-zA-Z0-9]{5,15}$";

    /**
     * 正则表达式：乐动验证用户名(输入长度范围为4-16位，字母或字母+数字的组合)
     */
    public static final String REGEX_USERNAME_V2 = "^[a-zA-Z0-9]{4,16}$";

    /**
     *
     */
    public static final String REGISTER_USERNAME = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,16}$";

    /**
     * USDT钱包地址验证、不含中文
     */
    public static final String REGEX_USDT_ADDRESS = "^[a-zA-Z0-9]{16,64}$";

    /**
     * ERC20协议钱包地址必须以0x开头
     */
    public static final String REGEX_USDT_ERC20_ADDRESS = "^(0x).*";

    /**
     * TRC20协议钱包地址必须以T开头
     */
    public static final String REGEX_USDT_TRC20_ADDRESS = "^T.*";

    /**
     * 正则表达式：验证后台用户名
     */
    public static final String REGEX_ADMINNAME = "^[a-zA-Z0-9]{3,16}$";

    /**
     * 正则表达式：验证密码
     */
    public static final String REGEX_PASSWORD = "^[a-zA-Z0-9]{8,20}$";

    /**
     * 正则表达式：乐动验证密码(输入长度范围为6-12位，可以为数字，字母或字母+数字的组合)
     */
    public static final String REGEX_PASSWORD_V2 = "^[a-zA-Z0-9]{6,12}$";

    /**
     * 正则表达式：校验密码至少为字母，数字，符号两种组成，不包含空格中文
     */
    public static final String REGEX_MIXTURE_PASSWORD = "(?!^\\d+$)(?!^[A-Za-z]+$)(?!^[^A-Za-z0-9]+$)(?!^.*[\\u4E00-\\u9FA5].*$)^\\S{8,20}$";

    /**
     * 正则表达式：强验证密码
     */
    public static final String REGEX_STRONG_PASSWORD = "^(?![\\d]+$)(?![a-zA-Z]+$)(?![`~!@#$%^&*()+=|{}':;',\\[\\].<>/?]+$)[\\da-zA-Z`~!@#$%^&*()+=|{}':;',\\[\\].<>/?]{8,20}$";

    /**
     * 正则表达式：验证手机号
     */
    public static final String REGEX_MOBILE = "^((13[0-9])|(14[5,7,9])|(15([0-3]|[5-9]))|(16[5,6])|(17[0,1,2,3,5,6,7,8])|(18[0-9])|(19[1|8|9]))\\d{8}$";

    /**
     * 新注册手机号，取消 165 170 171三个虚拟号段
     * 正则表达式：验证手机号
     */
    public static final String REGEX_ALLOW_MOBILE = "^((13[0-9])|(14[5,7,9])|(15([0-3]|[5-9]))|(166)|(17[2,3,5,6,7,8])|(18[0-9])|(19[1,5,8,9]))\\d{8}$";


    /**
     * 正则表达式：台湾手机号码
     */
    public static final String REGEX_TAI_MOBILE = "(09[0-9])\\d{8}";
    /**
     * 正则表达式：香港手机号码
     */
    public static final String REGEX_KONG_MOBILE = "(3|5|6|8|9)\\d{7}";


    /**
     * 正则表达式：提取手机号
     */
    public static final String REGEX_EXTRACT_MOBILE = "((13[0-9])|(14[5,7,9])|(15([0-3]|[5-9]))|(166)|(17[1,2,3,5,6,7,8])|(18[0-9])|(19[1|8|9]))\\d{8}";


    /**
     * 正则表达式：验证银行卡号
     */
    public static final String REGEX_BANKCARD = "^\\d{16}|\\d{17}|\\d{18}|\\d{19}$";

    /**
     * 正则表达式：提取银行卡号
     */
    public static final String REGEX_EXTRACT_BANKCARD = "\\d{16}|\\d{17}|\\d{18}|\\d{19}";

    /**
     * 正则表达式：简单验证国际手机号
     */
    public static final String REGEX_MOBILE_I18N = "^\\d{7,11}";

    /**
     * 正则表达式：简单验证国际电话区号
     */
    public static final String REGEX_MOBILE_AREACODE = "^\\d{1,4}";

    /**
     * 正则表达式：验证邮箱
     */
    public static final String REGEX_EMAIL = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";

    /**
     * 正则表达式：提取邮箱
     */
    public static final String REGEX_EXTRACT_EMAIL = "\\w[-\\w.+]*@([A-Za-z0-9][-A-Za-z0-9]+\\.)+[A-Za-z]{2,14}";

    /**
     * 正则表达式：验证汉字
     */
    public static final String REGEX_CHINESE = "^[\\u4e00-\\u9fa5]+$";

    /**
     * 正则表达式：验证英文
     */
    public static final String REGEX_ENGLISH = "^[A-Za-z\\s]*$";

    /**
     * 正则表达式：验证少数民族姓名
     */
    public static final String REGEX_MINORITY = "^[\\u4E00-\\u9FA5\\s]+(·[\\u4E00-\\u9FA5]+)*$";


    /**
     * 正则表达式：验证身份证
     */
    public static final String REGEX_ID_CARD = "(^\\d{18}$)|(^\\d{15}$)";

    /**
     * 正则表达式：验证URL
     */
    public static final String REGEX_URL = "http(s)?://([\\w-]+\\.)+[\\w-]+(/[\\w- ./?%&=]*)?";

    /**
     * 正则表达式：验证IP地址
     */
    public static final String REGEX_IP_ADDR = "(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})(\\.(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})){3}";

    /**
     * 正则表达式：验证IP前三段
     */
    public static final String REGEX_IP_ADDR_IGNORRE_LAST = "(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})(\\.(2(5[0-5]{1}|[0-4]\\d{1})|[0-1]?\\d{1,2})){2}";


    /**
     * 正则表达式：名字验证  以中文开头，后用“·”连接（一位或多位），例如：阿孜古丽·尼加提
     */
    public static final String REGEX_NAME = "^[\\u4E00-\\u9FA5]{2,15}(?:·[\\u4E00-\\u9FA5]{2,15})+$";

    /**
     * 匹配操作审核姓名
     */
    public static final String REGEX_OPERATION_NAME = "(?<=姓名(:|：)).*?((?=,.[^,]*(:|：))|$)";

    /**
     * 匹配操作审核姓名
     */
    public static final String REGEX_JSON_NAME = "\"name\":\"(.*?)\\\"";


    public static final String REGEX_NAME_V2 = "^[\\u4E00-\\u9FA5A-Za-z\\s]+(·[\\u4E00-\\u9FA5A-Za-z]+)*$";

    public static final String JSON_STRING_VALUE = "\"%s\":\"(.*?)\"";

    public static final String JSON_OBJECT_VALUE = "\"%s\":(.*?)(}|,)";

    public static boolean isNameV2(String name) {
        return Pattern.matches(REGEX_NAME_V2, name);
    }



    /**
     * 校验用户名
     *
     * @param username
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isUsername(String username) {
        return Pattern.matches(REGEX_USERNAME_V2, username);
    }

    public static boolean isRegisterUsername(String username) {
        return Pattern.matches(REGISTER_USERNAME, username);
    }

    /**
     * 校验用户名
     *
     * @param username 用户名
     * @param regex    规则
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isUsernameV2(String username, String regex) {
        return Pattern.matches(regex, username);
    }

    /**
     * 校验用户名
     *
     * @param username
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isAdminName(String username) {
        return Pattern.matches(REGEX_ADMINNAME, username);
    }

    /**
     * 校验密码
     *
     * @param password
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isPassword(String password) {
        return Pattern.matches(REGEX_PASSWORD, password);
    }

    /**
     * 校验密码
     *
     * @param password 密码
     * @param regex    规则
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isPasswordV2(String password, String regex) {
        return Pattern.matches(regex, password);
    }

    /**
     * 校验密码至少为数字，字母，下划线的组合
     *
     * @param password
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isMixturePassword(String password) {
        return Pattern.matches(REGEX_MIXTURE_PASSWORD, password);
    }

    /**
     * 校验密码(强校验)
     *
     * @param password
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isStrongPassword(String password) {
        return Pattern.matches(REGEX_STRONG_PASSWORD, password);
    }

    /**
     * 校验手机号
     *
     * @param mobile
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isMobile(String mobile) {
        return Pattern.matches(REGEX_MOBILE, mobile);
    }

    /**
     * 校验手机号
     *
     * @param mobile
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isAllowNewMobile(String mobile) {
        return Pattern.matches(REGEX_ALLOW_MOBILE, mobile);
    }

    /**
     * 校验银行卡号
     *
     * @param bankCard
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isBankCard(String bankCard) {
        return Pattern.matches(REGEX_BANKCARD, bankCard);
    }

    /**
     * 校验邮箱
     *
     * @param email
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isEmail(String email) {
        return Pattern.matches(REGEX_EMAIL, email);
    }

    /**
     * 校验汉字
     *
     * @param chinese
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isChinese(String chinese) {
        return Pattern.matches(REGEX_CHINESE, chinese);
    }

    /**
     * 校验英文
     *
     * @param name
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isEnglish(String name) {
        return Pattern.matches(REGEX_ENGLISH, name);
    }

    /**
     * 校验少数民族
     *
     * @param name
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isMinority(String name) {
        return Pattern.matches(REGEX_MINORITY, name);
    }

    /**
     * 校验身份证
     *
     * @param idCard
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isIDCard(String idCard) {
        return Pattern.matches(REGEX_ID_CARD, idCard);
    }

    /**
     * 校验URL
     *
     * @param url
     * @return 校验通过返回true，否则返回false
     */
    public static boolean isUrl(String url) {
        return Pattern.matches(REGEX_URL, url);
    }

    /**
     * 校验IP地址
     *
     * @param ipAddr
     * @return
     */
    public static boolean isIPAddr(String ipAddr) {
        return Pattern.matches(REGEX_IP_ADDR, ipAddr);
    }

    /**
     * 校验IP前三段地址
     *
     * @param ipAddr
     * @return
     */
    public static boolean isIPAddrIgnoreLast(String ipAddr) {
        return Pattern.matches(REGEX_IP_ADDR_IGNORRE_LAST, ipAddr);
    }

    public static boolean isName(String name) {
        return Pattern.matches(REGEX_NAME, name);
    }

    public static boolean isUsdtAddress(String address) {
        return Pattern.matches(REGEX_USDT_ADDRESS, address);
    }

    /**
     * 是否是ERC20协议的钱包地址
     *
     * @param address
     * @return
     */
    public static boolean isERC20WalletAddress(String address) {
        return Pattern.matches(REGEX_USDT_ERC20_ADDRESS, address);
    }

    /**
     * 是否是TRC20协议的钱包地址
     *
     * @param address
     * @return
     */
    public static boolean isTRC20WalletAddress(String address) {
        return Pattern.matches(REGEX_USDT_TRC20_ADDRESS, address);
    }

    /**
     * 只能获取String类型的值，无法获取null
     * @param key json的key
     * @param json json字符串
     * @return
     */
    public static Set<String> getStringValue(String key, String json){
        Set<String> stringSet = Sets.newHashSet();
        String regex = String.format(JSON_STRING_VALUE,key);
        Matcher matcher = Pattern.compile(regex).matcher(json);
        while (matcher.find()) {
            stringSet.add(matcher.group(1));
        }
        return stringSet;
    }

    /**
     * 获取所有类型数据的字符串，String类型的会带"" 需要手动去掉
     * @param key
     * @param json
     * @return
     */
    public static Set<String> getObjectValue(String key, String json){
        Set<String> stringSet = Sets.newHashSet();
        String regex = String.format(JSON_OBJECT_VALUE,key);
        Matcher matcher = Pattern.compile(regex).matcher(json);
        while (matcher.find()) {
            stringSet.add(matcher.group(1));
        }
        return stringSet;
    }
}