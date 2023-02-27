package com.ruoyi.common.core.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;

public class DateUtil {
    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    public static final String YYYYMMDDHHMMSS2 = "yyyyMMddHHmmssSSS";

    public static final String YYYYMMDD = "yyyyMMdd";

    public static final String YYYYMM = "yyyyMM";

    public static final String YYYY = "yyyy";

    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    public static final String YYYY_MM_DD_HH_MM_SS2 = "yyyy-MM-dd HH.mm.ss";

    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    public static final String YYYY_MM = "yyyy-MM";

    public static final String MM_DD = "MM-dd";

    public static final String HHMMSS = "HHmmss";

    public static final String HH = "HH";

    public static final String UTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final String getFormateDate(String formate) {
        SimpleDateFormat f = new SimpleDateFormat(formate, Locale.CHINA);
        return f.format(new Date());
    }

    public static String getCustomFormateDate(String formate, Object date) {
        SimpleDateFormat f = new SimpleDateFormat(formate, Locale.US);
        return f.format(date);
    }

    public static final String getFormateDate(Date date, String formate) {
        SimpleDateFormat f = new SimpleDateFormat(formate, Locale.US);
        return f.format(date);
    }

    public static final String getDateTime() {
        return getFormateDate("yyyy-MM-dd HH:mm:ss");
    }

    public static final String getDate() {
        return getFormateDate("yyyy-MM-dd");
    }

    public static final String getDate(Object date) {
        if (date == null)
            return "";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        return f.format(date);
    }

    public static final String getDateTime(Object date) {
        if (date == null)
            return "";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return f.format(date);
    }

    public static final String getYear() {
        return getFormateDate("yyyy");
    }

    public static final String getShortYear() {
        return getFormateDate("yy");
    }

    public static final String getMonth() {
        return getFormateDate("MM");
    }

    public static final String getShortMonth() {
        return getFormateDate("M");
    }

    public static final String getDay() {
        return getFormateDate("dd");
    }

    public static final String getShortDay() {
        return getFormateDate("d");
    }

    public static final String getTime() {
        return getFormateDate("HH:mm:ss");
    }

    public static final boolean isDate(String dateStr) {
        Date dt = parseSimpleDate(dateStr);
        if (dt != null)
            return true;
        return (parseSimpleDateTime(dateStr) != null);
    }

    public static final boolean isDate(String pattern, String dateStr) {
        return (parseSimpleDT(pattern, dateStr) != null);
    }

    public static final Date parseSimpleDateTime(String dateStr) {
        return parseSimpleDT("yyyy-MM-dd HH:mm:ss", dateStr);
    }

    public static final Date parseSimpleDate(String dateStr) {
        return parseSimpleDT("yyyy-MM-dd", dateStr);
    }

    public static final Date parseSimpleTime(String timeStr) {
        return parseSimpleDT("HH:mm:ss", timeStr);
    }

    public static final Date parseSimpleDateTimeHM(String dateStr) {
        return parseSimpleDT("yyyy-MM-dd HH:mm", dateStr);
    }

    public static final Date parseSimpleDT(String pattern, String dateStr) {
        try {
            return (new SimpleDateFormat(pattern, Locale.US)).parse(dateStr);
        } catch (ParseException ex) {
            return null;
        }
    }

    public static final int defferDate(Date date1) {
        Date date2 = new Date();
        Long d2 = Long.valueOf(date2.getTime());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        long d1 = 0L;
        try {
            d1 = sdf.parse(date1.toString()).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        int days = (int)((d1 - d2.longValue()) / 86400000L);
        return Math.abs(days);
    }

    public static final int compareDate(Date date1, Date date2) {
        if (date1.before(date2))
            return -1;
        if (date1.after(date2))
            return 1;
        return 0;
    }

    public static final boolean isBefore(Date date1, Date date2) {
        if (date1 == null || date2 == null)
            return false;
        return date1.before(date2);
    }

    public static final boolean isBeforeNow(Date date1) {
        return isBefore(date1, Calendar.getInstance().getTime());
    }

    public static final boolean isAfter(Date date1, Date date2) {
        if (date1 == null || date2 == null)
            return false;
        return date1.after(date2);
    }

    public static final boolean isAfterNow(Date date1) {
        return isAfter(date1, Calendar.getInstance().getTime());
    }

    public static final boolean isEquals(Date date1, Date date2) {
        if (date1 == null || date2 == null)
            return false;
        return date1.equals(date2);
    }

    public static final boolean isEqualsNow(Date date1) {
        return isEquals(date1, Calendar.getInstance().getTime());
    }

    public static final Date getNowDate(int... deviation) {
        return setDate(new Date(), deviation);
    }

    public static final Date setDate(Date date, int... deviation) {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.setTime(date);
        if (deviation.length < 1)
            return cal.getTime();
        int[] filed = { 1, 2, 5, 11, 12, 13 };
        for (int i = 0; i < deviation.length; i++)
            cal.add(filed[i], deviation[i]);
        return cal.getTime();
    }

    public static final String dateTimeTips(Date dt) {
        Calendar cal = Calendar.getInstance();
        long times = cal.getTimeInMillis() - dt.getTime();
        if (times <= 60000L)
            return "1分钟前";
        if (times <= 3600000L)
            return (times / 60000L) + "分钟前";
        if (times <= 86400000L)
            return (times / 3600000L) + "小时前";
        if (times <= 604800000L)
            return (times / 86400000L) + "天前";
        if (times <= 2592000000L)
            return (times / 604800000L) + "星期前";
        if (times <= 31104000000L)
            return (times / 2592000000L) + "个月前";
        return (times / 31104000000L) + "年前";
    }

    public static final String dateTips(String dateStr) {
        Date dt = parseSimpleDate(dateStr);
        if (dt == null)
            return dateStr;
        return dateTimeTips(dt);
    }

    public static final String dateTimeTips(String dateTime) {
        Date dt = parseSimpleDateTime(dateTime);
        if (dt == null)
            return dateTime;
        return dateTimeTips(dt);
    }

    public static Date addMinutes(Date date, int minutes) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(12, minutes);
        return c.getTime();
    }

    public static Date addHour(Date date, int hour) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(10, hour);
        return c.getTime();
    }

    public static Date getDate(int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.set(11, hour);
        c.set(12, minute);
        c.set(13, second);
        return c.getTime();
    }

    public static String getWeekBefore() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.add(3, -1);
        return df.format(cal.getTime());
    }

    public static String getWeekAfter() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.add(3, 1);
        return df.format(cal.getTime());
    }

    public static String getMonthBefore() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.add(2, -1);
        return df.format(cal.getTime());
    }

    public static String getLastMonth(String format) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat(format);
        cal.add(2, -1);
        return df.format(cal.getTime());
    }

    public static String getMonthAfter() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.add(2, 1);
        return df.format(cal.getTime());
    }

    public static String getYearBefore() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        cal.add(1, -1);
        return df.format(cal.getTime());
    }

    public static Date addDate(Date date, int day) {
        if (date == null)
            return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(6, day);
        return calendar.getTime();
    }

    public static Date stringToDate(String value) throws ParseException {
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM");
        if (null == value || "".equals(value.trim()))
            return null;
        value = value.trim();
        value = value.replaceAll("/", "-");
        value = value.replace("年", "-").replace("月", "-").replace("日", " ")
                .replace("时", ":").replace("分", ":").replace("秒", "");
        if (value.length() <= 7) {
            try {
                Integer.parseInt(value);
                if (value.length() == 5) {
                    value = value.substring(0, 4) + "-0" + value.substring(4, 5);
                } else if (value.length() == 6) {
                    value = value.substring(0, 4) + "-" + value.substring(4, 6);
                }
            } catch (Exception e) {
                value = value.replace("月", "");
                        String str1 = "";
                String str2 = "";
                if (value.indexOf("-") != -1) {
                    str1 = value.split("-")[0];
                    str2 = value.split("-")[1];
                }
                if (str2.length() == 1)
                    str2 = "0" + str2;
                value = str1 + "-" + str2;
            }
            return sdfDate.parse(value);
        }
        String year = "";
        String month = "";
        String date = "";
        if (value.indexOf(" ") != -1) {
            String rq = value.split(" ")[0];
            String sj = value.split(" ")[1];
            if (rq.indexOf("-") != -1) {
                year = rq.split("-")[0];
                month = rq.split("-")[1];
                date = rq.split("-")[2];
            }
            if (month.length() == 1)
                month = "0" + month;
            if (date.length() == 1)
                date = "0" + date;
            if (sj.length() == 5)
                sj = sj + ":00";
            value = year + "-" + month + "-" + date + " " + sj;
        } else {
            try {
                Integer.parseInt(value);
                if (value.length() == 8)
                    value = value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8) + " 00:00:00";
            } catch (Exception e) {
                value = value.replace("月", "-").replace("日", "");
                if (value.indexOf("-") != -1) {
                    year = value.split("-")[0];
                    month = value.split("-")[1];
                    date = value.split("-")[2];
                }
                if (month.length() == 1)
                    month = "0" + month;
                if (date.length() == 1)
                    date = "0" + date;
                value = year + "-" + month + "-" + date + " 00:00:00";
            }
        }
        return sdfTime.parse(value);
    }

    public static int getCurrentYear() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(1);
    }

    public static Date transferLongToDate(String dateFormat, Long millSec) {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        Date date = new Date(millSec.longValue());
        return date;
    }

    public static Date TimeStamp2Date(String timestampString, String formats) {
        if (timestampString.length() > 10)
            timestampString = timestampString.substring(0, 10);
        if (StringUtils.isBlank(formats))
            formats = "yyyy-MM-dd HH:mm:ss";
        Long timestamp = Long.valueOf(Long.parseLong(timestampString) * 1000L);
        String date = (new SimpleDateFormat(formats)).format(new Date(timestamp.longValue()));
        return parseSimpleDateTime(date);
    }

    public static int differentDaysByMillisecond(Date date1, Date date2) {
        int days = (int)((date2.getTime() - date1.getTime()) / 86400000L);
        return days;
    }

    public static String formatBTITime(Date inDate) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(inDate);
    }

    public static Date getMinTime(Date date) {
        return stringToDate(formatForTime(date), "yyyy-MM-dd");
    }

    /**
     * 最大时间
     *
     * @param date
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 23:59:59
     */
    public static Date getMaxTime(Date date) {
        date = getMinTime(date);
        String dateStr = formatForTime(date);
        dateStr = dateStr.replaceAll("00:00:00", "23:59:59");
        return stringToDate(dateStr, YYYY_MM_DD_HH_MM_SS);
    }

    public static Date stringToDate(String date, String format) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(date);
        } catch (ParseException parseException) {
            return null;
        }
    }

    public static String formatForTime(Date date) {
        if (date == null)
            return "";
        String format = "yyyy-MM-dd HH:mm:ss";
        DateFormat df = new SimpleDateFormat(format);
        return df.format(date);
    }

    public static int diffDay(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int)((to - from) / 86400000L);
    }

    public static int diffHours(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int)((to - from) / 3600000L);
    }

    public static int diffMins(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int)((to - from) / 60000L);
    }

    public static int diffSecond(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int)((to - from) / 1000L);
    }

    public static void main(String[] args) throws ParseException {
        String dateStr = formatBTITime(new Date());
        System.out.println(dateStr);
    }
}
