package com.seektop.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringEncryptor {

    private static final String upperCaseChar = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encryptUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return username;
        }
        if (username.contains(",")) {
            username = username.substring(0, username.indexOf(","));
        }
        StringBuilder realname1 = new StringBuilder();
        char[] r = username.toCharArray();
        if (r.length == 1) {
            realname1.append(username);
        }
        if (r.length == 2) {
            realname1.append(username, 0, 1).append("*");
        }
        if (r.length > 2) {
            realname1.append(username, 0, 1).append("*").append(username.substring(username.length() - 1));
        }
        return realname1.toString();
    }

    public static String encryptUserName(String userName) {
        if (StringUtils.isEmpty(userName) || userName.length() <6) {
            return userName;
        }
        return simpleEncrypt(userName, 3,userName.length());
    }

    public static String encryptMobile(String mobile) {
        if (StringUtils.isEmpty(mobile) || mobile.length() <= 7) {
            return mobile;
        }
        return simpleEncrypt(mobile, 3, 7);
    }

    public static String encryptEmail(String email) {
        if (StringUtils.isEmpty(email) || email.length() <= 3 || !email.contains("@")) {
            return email;
        }
        int end = email.indexOf("@");
        return simpleEncrypt(email, 3, end);
    }

    private static String simpleEncrypt(String str, int from, int end) {
        int len = str.length();
        if (len <= from) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(str.substring(0, from));
        if (len <= end) {
            for (int i = 0; i < len - from; i++) {
                sb.append("*");
            }
        } else {
            for (int i = 0; i < end - from; i++) {
                sb.append("*");
            }
            sb.append(str.substring(end, str.length()));
        }
        return sb.toString();
    }


    /**

          * 脱敏地址字符串中的数字
          * @param address
          * @return
          */
    public static String encryptAddress(String address) {
        if(address == null) {
            return "";
        }
        char[] aa = address.toCharArray();
        String newAddr = "";
        String temp = "";
        for(int a = aa.length - 1; a >= 0; a--) {
            if((int)aa[a] >= '0' && (int)aa[a] <= '9') {
                temp = aa[a] + temp;
            } else {
                if(temp.length() > 0) {
                    int l = 2;
                    newAddr = temp.substring((temp.length() < l ? l : temp.length()) - 1, temp.length()) + newAddr;
                    if(temp.length() - 1<1){
                        newAddr = "*" + newAddr;
                    }else{
                        for(int b = 0; b < temp.length() - 1; b++) {
                            newAddr = "*" + newAddr;
                        }
                    }
                }
                temp = "";
                newAddr = aa[a] + newAddr;
            }
        }
        //武胜路333号1层
        Pattern p = Pattern.compile(".*\\d+.*");
        Matcher m = p.matcher(temp);
        if(m.matches()){
            if(temp.length()>1){
                newAddr="*"+temp.substring(temp.length()-1,temp.length())+newAddr;
                for (int i = 0; i < temp.length()-2; i++) {
                    newAddr="*"+newAddr;
                }
            }else{
                newAddr="*"+newAddr;
            }
        }
        return newAddr;
    }
    public static String commaSplitEncrypt(String source, Function<String,String> parse){
        String[] split = source.split(",");
        for (String item : split) {
            source = source.replace(item,parse.apply(item.trim()));
        }
        return source;
    }
    public static String encryptOptFunction(String source, String reg, Function<String, String> encrypt) {
        if(org.apache.commons.lang3.StringUtils.isEmpty(source) || org.apache.commons.lang3.StringUtils.isEmpty(reg)) return "";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            source = source.replaceAll(matcher.group(), encrypt.apply(matcher.group()));
        }
        return source;
    }
    public static String encryptOpt(String source, List<Function<String, String>> functions) {
        if(org.apache.commons.lang3.StringUtils.isEmpty(source)) return source;
        for (Function<String, String> function : functions) {
            try {
                source = function.apply(source);
            }catch (Exception e){
//                log.error("脱敏操作失败 源数据：{} 脱敏方法：{}",opt,function);
            }
        }
        return source;
    }


    public static String encryptBankCard(String cardNo) {
        if(StringUtils.isBlank(cardNo))
            return cardNo;
        return simpleEncrypt(cardNo, 4, cardNo.length() - 4);
    }



    /**
     * 将数字转换成大写字符串
     *
     * @param num
     * @param len
     * @return
     */
    public static String numToUpperString(long num, int len) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.insert(0, upperCaseChar.charAt((int) (num % upperCaseChar.length())));
            num = num / upperCaseChar.length();
        }
        if (sb.length() > len) {
            return sb.substring(0, len);
        }
        int length = sb.length();
        for (int i = 0; i < len - length; i++) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }



    /**
     * 数字转换成 大写中文
     *
     * @param intInput
     * @return
     */
    public static String toCH(int intInput) {
        String si = String.valueOf(intInput);
        String sd = "";
        if (si.length() == 1) {
            sd += GetCH(intInput);
            return sd;
        } else if (si.length() == 2) {
            if (si.substring(0, 1).equals("1")) {
                sd += "十";
            } else {
                sd += (GetCH(intInput / 10) + "十");
            }
            sd += toCH(intInput % 10);
        } else if (si.length() == 3) {
            sd += (GetCH(intInput / 100) + "百");
            if (String.valueOf(intInput % 100).length() < 2) {
                sd += "零";
            }
            sd += toCH(intInput % 100);
        }
        return sd;
    }

    private static String GetCH(int input) {
        String sd = "";
        switch (input) {
            case 1:
                sd = "一";
                break;
            case 2:
                sd = "二";
                break;
            case 3:
                sd = "三";
                break;
            case 4:
                sd = "四";
                break;
            case 5:
                sd = "五";
                break;
            case 6:
                sd = "六";
                break;
            case 7:
                sd = "七";
                break;
            case 8:
                sd = "八";
                break;
            case 9:
                sd = "九";
                break;
            default:
                break;
        }
        return sd;
    }

    public static String encryptOptFunction2(String source, String reg, Function<String, String> encrypt) {
        if(StringUtils.isEmpty(source) || StringUtils.isEmpty(reg)) return "";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            source = source.replaceAll(matcher.group(1), encrypt.apply(matcher.group(1)));
        }
        return source;
    }

    /**
     * 加密商户密钥 （固定长度）
     *
     * @param key
     * @return
     */
    public static String encryptKey(String key) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isEmpty(key)) {
            return key;
        }
        if(key.length() > 4){
            sb.append(key.substring(0,2));
            sb.append("******");
            sb.append(key.substring(key.length()-2 , key.length()));
            return  sb.toString();
        }

        if(key.length() > 2){
            sb.append(key.substring(0,1));
            sb.append("********");
            sb.append(key.substring(key.length()-1 , key.length()));
            return  sb.toString();
        }
        return "**********";
    }

    public static String usernameFormat(String username){
        if (StringUtils.isBlank(username)) {
            return username;
        }
        int length = username.length();
        StringBuilder name = new StringBuilder();
        name.append(username, 0, length >=2 ? 2 : 1);
        name.append("***").append(username, length - 1, length);
        return name.toString();
    }
}
