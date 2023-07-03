package com.seektop.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DateUtils {

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

    /**
     * 获取月份字符串
     *
     * @param f
     * @param format
     * @return
     */
    public static String getMonth(int f, String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MONTH, f);
        DateFormat df = new SimpleDateFormat(format);
        String result = df.format(calendar.getTime());
        return result;
    }

    public static Date addMonth(int f, Date dateTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.MONTH, f);
        return calendar.getTime();
    }

    public static String getDay(int f, String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, f);
        DateFormat df = new SimpleDateFormat(format);
        String result = df.format(calendar.getTime());
        return result;
    }

    public static String addDay(int f, String format, String dateTime) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(toDate(dateTime, "yyyy-MM-dd HH:mm:ss"));
        calendar.add(Calendar.DAY_OF_MONTH, f);
        DateFormat df = new SimpleDateFormat(format);
        String result = df.format(calendar.getTime());
        return result;
    }

    public static String addDay(int f, String format, Date dateTime) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.DAY_OF_MONTH, f);
        DateFormat df = new SimpleDateFormat(format);
        String result = df.format(calendar.getTime());
        return result;
    }

    public static Date addDay(int f, Date dateTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.DAY_OF_MONTH, f);
        return calendar.getTime();
    }

    public static Date addSec(int f, Date dateTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.SECOND, f);
        return calendar.getTime();
    }

    public static Date addMin(int f, Date dateTime) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.MINUTE, f);
        return calendar.getTime();
    }

    public static Date addHours(int f, Date dateTime) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTime);
        calendar.add(Calendar.HOUR, f);
        return calendar.getTime();
    }

    public static String getDay(int f, String format, String dateStr) throws ParseException {
        DateFormat df = new SimpleDateFormat(format);
        Date date = df.parse(dateStr);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, f);

        String result = df.format(calendar.getTime());
        return result;
    }

    public static String format(Date date, String format) {
        if (date == null) {
            return "";
        }
        DateFormat df = new SimpleDateFormat(format);
        return df.format(date);
    }

    /**
     * 英文格式的时间转换
     *
     * @param date
     * @param format
     * @return
     */
    public static String format4English(Date date, String format) {
        if (date == null) {
            return "";
        }
        DateFormat df = new SimpleDateFormat(format, Locale.ENGLISH);
        return df.format(date);
    }

    public static Date parse(String date, String format) throws ParseException {
        if (date == null) {
            return null;
        }
        DateFormat df = new SimpleDateFormat(format);
        return df.parse(date);
    }

    /**
     * yyyy-MM-dd
     *
     * @param date
     * @return
     */
    public static Date getCurDate(Date date, String format) throws ParseException {
        if (date == null) {
            return new Date();
        }
        DateFormat df = new SimpleDateFormat(format);
        return toDate(df.format(date), format);
    }

    /**
     * yyyy-MM-dd HH:mm:ss
     *
     * @param date
     * @return
     */
    public static String formatForTime(Date date) {
        if (date == null) {
            return "";
        }
        String format = "yyyy-MM-dd HH:mm:ss";
        DateFormat df = new SimpleDateFormat(format);
        return df.format(date);
    }

    /**
     * yyyy-MM-dd
     *
     * @param date
     * @return
     */
    public static String getStrCurDate(Date date, String format) {
        if (date == null) {
            return "";
        }
        DateFormat df = new SimpleDateFormat(format);
        return df.format(date);
    }

    /**
     * 当前年月日时分
     *
     * @return
     */
    public static String getCurrDateStr12() {
        return format(new Date(), "yyyyMMddHHmm");
    }

    /**
     * 当前年月日时分秒
     *
     * @return
     */
    public static String getCurrDateStr14() {
        return format(new Date(), "yyyyMMddHHmmss");
    }

    /**
     * 当前时分秒
     *
     * @return
     */
    public static String getCurrDateStr16() {
        return format(new Date(), "HHmmss");
    }

    public static boolean compareWithNow(Date date, int hours) {
        long difTime = 1000 * 60 * 60 * hours;
        Date now = new Date();
        long nowTime = now.getTime();
        long otherTime = date.getTime();
        boolean result = nowTime - otherTime > difTime;
        return result;
    }

    public static Date toDate(String str, String format) throws ParseException {
        DateFormat df = new SimpleDateFormat(format);
        return df.parse(str);
    }

    /**
     * 获取当天凌晨0点0分0秒Date
     *
     * @param currentTime
     * @return
     */
    public static Date getStartWithCurrentDay(Date currentTime) {

        Calendar calendar = Calendar.getInstance();
        if (null != currentTime)
            calendar.setTime(currentTime);
        calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0,
                0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new Date(calendar.getTimeInMillis());
    }

    /**
     * 获取当天23点59分59秒Date
     *
     * @param currentTime
     * @return
     */
    public static Date getEndWithCurrentDay(Date currentTime) {
        Calendar calendar = Calendar.getInstance();
        if (null != currentTime)
            calendar.setTime(currentTime);
        calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 23,
                59, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return new Date(calendar.getTimeInMillis());
    }

    /**
     * 获得日期数组
     *
     * @param calendarType 日期跨度的类型，
     */

    public static Date[] getDayArrays(Date start, Date end, int calendarType) {
        ArrayList<Date> ret = new ArrayList<Date>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        Date tmpDate = calendar.getTime();
        long endTime = end.getTime();
        while (tmpDate.before(end) || tmpDate.getTime() == endTime) {
            ret.add(calendar.getTime());
            calendar.add(calendarType, 1);
            tmpDate = calendar.getTime();
        }

        Date[] dates = new Date[ret.size()];
        return ret.toArray(dates);
    }

    /**
     * 获得日期字符串数组
     *
     * @param start
     * @param end
     * @return
     * @throws ParseException
     */
    public static String[] getDayArrays(String start, String end) throws ParseException {
        Date dateStart = DateUtils.toDate(start, "yyyy-MM-dd");
        Date dateEnd = DateUtils.toDate(end, "yyyy-MM-dd");
        Date[] strArray = getDayArrays(dateStart, dateEnd, Calendar.DAY_OF_YEAR);
        String[] retArray = new String[strArray.length];
        int index = 0;
        for (Date string : strArray) {
            retArray[index] = DateUtils.format(string, "yyyy-MM-dd");
            System.out.println(DateUtils.format(string, "yyyy-MM-dd"));
            index++;
        }
        return retArray;
    }

    public static String getCurrentTime() {
        String beginTime = "";
        String endTime = "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR) >= 3 && cal.get(Calendar.HOUR) < 24) {
            beginTime = sdf.format(cal.getTime()) + " 03:00:00";
            cal.add(Calendar.DATE, 1);
            endTime = sdf.format(cal.getTime()) + " 03:00:00";
        } else {
            endTime = sdf.format(cal.getTime()) + " 03:00:00";
            cal.add(Calendar.DATE, -1);
            beginTime = sdf.format(cal.getTime()) + " 03:00:00";
        }
        return beginTime + "#" + endTime;
    }

    public static String getDateByDay(Integer day) {
        Date dNow = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dNow);
        calendar.add(Calendar.DAY_OF_MONTH, day);
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());

    }

    public static boolean timeBetween(String fromTime, String endTime, Date valDate) {
        if (StringUtils.isEmpty(fromTime) && StringUtils.isEmpty(endTime)) {
            return true;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String valTime = sdf.format(valDate);
        if (StringUtils.isEmpty(fromTime)) {
            return Integer.parseInt(valTime.replace(":", "")) <= Integer.parseInt(endTime.replace(":", ""));
        }
        if (StringUtils.isEmpty(endTime)) {
            return Integer.parseInt(valTime.replace(":", "")) >= Integer.parseInt(fromTime.replace(":", ""));
        }
        int from = Integer.parseInt(fromTime.replace(":", ""));
        int end = Integer.parseInt(endTime.replace(":", ""));
        int time = Integer.parseInt(valTime.replace(":", ""));
        if (from <= end) {
            return time <= end && time >= from;
        } else {
            return time <= end || time >= from;
        }
    }

    public static String secToTime(int time) {
        String timeStr = null;
        int hour = 0;
        int minute = 0;
        int second = 0;
        if (time <= 0)
            return "00:00:00";
        else {
            minute = time / 60;
            if (minute < 60) {
                second = time % 60;
                timeStr = "00:" + unitFormat(minute) + ":" + unitFormat(second);
            } else {
                hour = minute / 60;
                minute = minute % 60;
                second = time - hour * 3600 - minute * 60;
                timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
            }
        }
        return timeStr;
    }

    public static String unitFormat(int i) {
        String retStr = null;
        if (i >= 0 && i < 10)
            retStr = "0" + Integer.toString(i);
        else
            retStr = "" + i;
        return retStr;
    }

    public static Date getFirstDayOfWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.addDay(-1, date));
        calendar.set(Calendar.DAY_OF_WEEK, 2);
        return org.apache.commons.lang3.time.DateUtils.truncate(calendar.getTime(), Calendar.DATE);
    }

    /**
     * 日期最早只能到 ${diffDay} 前，最早时间时分秒回传 00:00:00
     */
    @SuppressWarnings("deprecation")
    public static Date earliestDiffDay(Date date, int diffDay) {
        Assert.state(diffDay >= 0);
        Date now = new Date();
        Date earliestDate = DateUtils.addDay(-diffDay,
                org.apache.commons.lang3.time.DateUtils.truncate(now, Calendar.DATE));

        if (date == null || date.before(earliestDate)) {
            return earliestDate;
        } else {
            return date;
        }
    }

    @SuppressWarnings("deprecation")
    public static Integer getDifferenceDays(Date d1, Date d2) {
        Assert.notNull(d1);
        Assert.notNull(d2);
        long diff = d2.getTime() - d1.getTime();
        return (int) TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
    }

    public static Date getMondayStartDate(Date now) {
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        Date newDate = org.apache.commons.lang3.time.DateUtils.truncate(c, Calendar.DATE).getTime();

        if (newDate.after(now)) {
            newDate = org.apache.commons.lang3.time.DateUtils.addDays(newDate, -7);
        }

        return newDate;
    }

    /**
     * 两个日期相差的天数
     */
    public static int diffDay(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int) ((to - from) / (1000 * 60 * 60 * 24));
    }

    /**
     * 两个日期相差的小时
     */
    public static int diffHours(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int) ((to - from) / (1000 * 60 * 60));
    }

    /**
     * 两个日期相差的分钟数
     */
    public static int diffMins(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int) ((to - from) / (1000 * 60));
    }

    /**
     * 两个日期相差的秒数
     */
    public static int diffSecond(Date fromDate, Date endDate) {
        long to = endDate.getTime();
        long from = fromDate.getTime();
        return (int) ((to - from) / (1000));
    }

    /**
     * 获取指定时间段内所有的周
     *
     * @param startTime
     * @param endTime
     * @return
     */
    public static List<Date> getWeeks(Date startTime, Date endTime) {
        LinkedList<Date> list = new LinkedList<>();
        Date week = DateUtils.getFirstDayOfWeek(startTime);
        while (week.before(endTime)) {
            list.add(week);
            week = DateUtils.addDay(7, week);
        }
        return list;
    }

    public static List<Date> getDays(Date startTime, Date endTime) {
        LinkedList<Date> list = new LinkedList<>();
        Date date = org.apache.commons.lang3.time.DateUtils.truncate(startTime, Calendar.DATE);
        while (date.before(endTime)) {
            list.add(date);
            date = DateUtils.addDay(1, date);
        }
        return list;
    }

    /**
     * 获取当前日期上个月的第一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 00:00:00
     */
    public static Date getFirstDayOfLastMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date date = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获取当前月的第一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 00:00:00
     */
    public static Date getFirstDayOfThisMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date date = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获取下个月的第一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 00:00:00
     */
    public static Date getFirstDayOfNextMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date date = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获取当前日期上个月的最后一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-31 23:59:59
     */
    public static Date getLastDayOfLastMonth() {
        Calendar calendar = Calendar.getInstance();
//        calendar.add(Calendar.MONTH, -1);
        calendar.set(Calendar.DAY_OF_MONTH, 0);
        Date date = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获取当前日期这个月的最后一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-31 23:59:59
     */
    public static Date getLastDayOfThisMonth() {
        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.DAY_OF_MONTH, ca.getActualMaximum(Calendar.DAY_OF_MONTH));
        Date date = ca.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获得指定日期所在的自然周的第一天，即周日
     *
     * @param date 日期
     * @return 自然周的第一天
     */
    public static Date getStartDayOfWeek(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.DAY_OF_WEEK, 1);
        date = c.getTime();
        return date;
    }

    /**
     * 获得指定日期所在的自然周的最后一天，即周六
     *
     * @param date
     * @return
     */
    public static Date getLastDayOfWeek(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.DAY_OF_WEEK, 7);
        date = c.getTime();
        return date;
    }

    /**
     * 获得指定日期所在的周的最后一天，即周日
     *
     * @param date
     * @return
     */
    public static Date getSundayOfWeek(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, 7);
        date = c.getTime();
        return getEndWithCurrentDay(date);
    }

    /**
     * 获得指定日期所在的周的最后一天，即周日
     *
     * @param date
     * @return
     */
    public static Date getSundayOfWeek(Date date, boolean endTime) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, 7);
        if (endTime) {
            c.add(Calendar.HOUR, +12);
        }
        String day_last = df.format(c.getTime());
        StringBuffer endStr = new StringBuffer().append(day_last).append(" 23:59:59");
//        day_last = endStr.toString();
        return stringToDate(endStr.toString(), YYYY_MM_DD_HH_MM_SS);
    }

    /**
     * 获取指定日期的周一
     *
     * @param date
     * @return
     */
    public static Date getThisWeekMonday(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        // 获得当前日期是一个星期的第几天
        int dayWeek = cal.get(Calendar.DAY_OF_WEEK);
        if (1 == dayWeek) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        // 设置一个星期的第一天，按中国的习惯一个星期的第一天是星期一
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        // 获得当前日期是一个星期的第几天
        int day = cal.get(Calendar.DAY_OF_WEEK);
        // 根据日历的规则，给当前日期减去星期几与一个星期第一天的差值
        cal.add(Calendar.DATE, cal.getFirstDayOfWeek() - day);
        return cal.getTime();
    }

    /**
     * 获得指定日期所在自然周的周一 最小时间值 2018-12-31 00:00:00
     *
     * @param date 日期
     * @return 自然周的第一天
     */
    public static Date getCurrentMonday(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(getThisWeekMonday(date));
        Date calendarTime = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(calendarTime.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获得指定日期所在自然周的周天 最大时间值 2019-01-01 23:59:59
     *
     * @param date
     * @return
     */
    public static Date getCurrentSunday(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(getThisWeekMonday(date));
        calendar.add(Calendar.DATE, 6);
        Date calendarTime = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(calendarTime.getTime()),
                ZoneId.systemDefault());
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获得某天最小时间 格式：2017-10-15 00:00:00
     *
     * @param date
     * @return
     */
    public static Date getStartOfDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 获得某天最大时间 格式：2017-10-15 23:59:59
     *
     * @param date
     * @return
     */
    public static Date getEndOfDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 判断某一时间是否在一个区间内
     *
     * @param sourceTime 时间区间,半闭合,如[10:00-20:00)
     * @param curTime    需要判断的时间 如10:00
     * @return
     * @throws IllegalArgumentException
     */
    public static boolean isInTime(String sourceTime, String curTime) {
        if (sourceTime == null || !sourceTime.contains("-") || !sourceTime.contains(":")) {
            throw new IllegalArgumentException("Illegal Argument arg:" + sourceTime);
        }
        if (curTime == null || !curTime.contains(":")) {
            throw new IllegalArgumentException("Illegal Argument arg:" + curTime);
        }
        String[] args = sourceTime.split("-");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            long now = sdf.parse(curTime).getTime();
            long start = sdf.parse(args[0]).getTime();
            long end = sdf.parse(args[1]).getTime();
            if (args[1].equals("00:00")) {
                args[1] = "24:00";
            }
            if (end < start) {
                if (now >= end && now < start) {
                    return false;
                } else {
                    return true;
                }
            } else {
                if (now >= start && now < end) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Illegal Argument arg:" + sourceTime);
        }

    }

    /**
     * 判断当前时间是否在一个区间内
     *
     * @param sourceTime 时间区间,eg:"09:00-23:00"
     * @return boolean
     * @author Chaims
     * @date 2019/1/22 12:05
     */
    public static boolean isInTime(String sourceTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();
        String strDate = sdf.format(date);
        // 截取当前时间时分
        int strDateH = Integer.parseInt(strDate.substring(11, 13));
        int strDateM = Integer.parseInt(strDate.substring(14, 16));
        String curTime = strDateH + ":" + strDateM;
        return isInTime(sourceTime, curTime);
    }

    public static boolean belongCalendar(Date nowTime, Date beginTime, Date endTime) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        nowTime = dateFormat.parse(dateFormat.format(nowTime));
        beginTime = dateFormat.parse(dateFormat.format(beginTime));
        endTime = dateFormat.parse(dateFormat.format(endTime));

        Calendar date = Calendar.getInstance();
        date.setTime(nowTime);
        Calendar begin = Calendar.getInstance();
        begin.setTime(beginTime);
        Calendar end = Calendar.getInstance();
        end.setTime(endTime);
        if (beginTime.after(endTime)) {
            if (date.after(begin) || date.before(end)) {
                return true;
            } else {
                return false;
            }
        }
        if (nowTime.compareTo(beginTime) == 0) {
            return true;
        }
        if (date.after(begin) && date.before(end)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取当前时间距离凌晨0点的秒数
     *
     * @return java.lang.Integer
     * @author Chaims
     * @date 2019/4/25 17:55
     */
    public static Integer getRemainSecondsOneDay() {
        Date currentDate = new Date();
        Calendar midnight = Calendar.getInstance();
        midnight.setTime(currentDate);
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        Integer seconds = (int) ((midnight.getTime().getTime() - currentDate.getTime()) / 1000);
        if (seconds.intValue() == 0) {
            seconds = 1;
        }

        return seconds;
    }

    /**
     * 获取当前月第一天
     *
     * @return
     */
    public static Date monthFirstDay() {
        Date now = new Date();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now.getTime()),
                ZoneId.systemDefault());
        LocalDateTime endOfDay = localDateTime.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 处理如 2016-11-19 18:15:12 +08:00 的问题,不需要后面的时区 date 有时区的日期 pattern 想转换的格式
     */
    public static Date handleDateZone(final String date, final String pattern) {
        int space = date.lastIndexOf(" ");
        LocalDateTime dt = LocalDateTime.parse(date.substring(0, space), DateTimeFormatter.ofPattern(pattern));
        return Date.from(dt.atZone(ZoneId.of(ZoneId.SHORT_IDS.get("CTT"))).toInstant());
    }

    /**
     * 年月日 转换美东时间 +12小时 后的 年月日 00:00:00 date 有时区的日期 pattern 想转换的格式
     */
    public static Date getStartOfUTC(Date date) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTime(getStartOfDay(date));
        rightNow.add(Calendar.HOUR, +12);
        return rightNow.getTime();
    }

    /**
     * 年月日 转换美东时间 +12小时 后的 年月日 23:59:59 date 有时区的日期
     */
    public static Date getEndOfUTC(Date date) {
        //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTime(getEndOfDay(date));
        rightNow.add(Calendar.HOUR, +12);
        return rightNow.getTime();
    }

    /**
     * 获取天的日期
     *
     * @param date
     * @return
     */
    public static int getDay(Date date) {
        if (date == null)
            return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取月份
     *
     * @param date
     * @return
     */
    public static int getMonth(Date date) {
        if (date == null)
            return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.MONTH) + 1;
    }

    /**
     * 获取年
     *
     * @param date
     * @return
     */
    public static int getYear(Date date) {
        if (date == null)
            return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.YEAR);
    }

    /**
     * 获取上周一
     *
     * @return
     */
    public static Date getMondayOfLastWeek(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, -7);
        return cal.getTime();
    }

    /**
     * 获取当前日期上个月的某一天
     *
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 00:00:00
     */
    public static Date getDayOfLastMonth(int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        Date date = calendar.getTime();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()),
                ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * StringToDate
     *
     * @param date
     * @return
     */
    public static Date stringToDate(String date, String format) {// yyyy-MM-dd HH:mm:ss
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(date);
        } catch (ParseException e) {
        }
        return null;
    }

    /**
     * 最小时间
     *
     * @param date
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 00:00:00
     */
    public static Date getMinTime(Date date) {
        return DateUtils.stringToDate(DateUtils.formatForTime(date), "yyyy-MM-dd");
    }

    /**
     * 最大时间
     *
     * @param date
     * @return yyyy-MM-dd HH:mm:ss 2019-01-01 23:59:59
     */
    public static Date getMaxTime(Date date) {
        date = DateUtils.getMinTime(date);
        String dateStr = formatForTime(date);
        dateStr = dateStr.replaceAll("00:00:00", "23:59:59");
        return stringToDate(dateStr, YYYY_MM_DD_HH_MM_SS);
    }

    /**
     * 获取当月某天的最小值
     *
     * @param date
     * @param day  01,02,03......29,30
     * @return
     */
    public static Date getDateMinTimeThisMounth(Date date, String day) {
        try {
            String ym = format(date, YYYYMM);
            StringBuilder sb = new StringBuilder();
            sb.append(ym).append(day).append("000000");
            date = toDate(sb.toString(), YYYYMMDDHHMMSS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    /**
     * 获取某月的上个某一天
     *
     * @param date
     * @param day
     * @return
     */
    public static Date getDateMinTimeLastMounth(Date date, String day) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, -1);
            String ym = format(cal.getTime(), YYYYMM);
            StringBuilder sb = new StringBuilder();
            sb.append(ym).append(day).append("000000");
            date = toDate(sb.toString(), YYYYMMDDHHMMSS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    /**
     * 获取当月某天的最大值
     *
     * @param date
     * @param day  01,02,03......29,30
     * @return
     */
    public static Date getDateMaxTimeThisMounth(Date date, String day) {
        try {
            String ym = format(date, YYYYMM);
            StringBuilder sb = new StringBuilder();
            sb.append(ym).append(day).append("235959");
            date = toDate(sb.toString(), YYYYMMDDHHMMSS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    /**
     * 获取前一天时间
     */
    public static Date getLastDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, -1);
        return getMaxTime(cal.getTime());
    }

    /**
     * 获取月第一天
     */
    public static Date getFirstDayMonth(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        Date theDate = calendar.getTime();

        // 月第一天
        GregorianCalendar gcLast = (GregorianCalendar) Calendar.getInstance();
        gcLast.setTime(theDate);
        gcLast.set(Calendar.DAY_OF_MONTH, 1);
        String day_first = df.format(gcLast.getTime());
        StringBuffer str = new StringBuffer().append(day_first).append(" 00:00:00");
        return stringToDate(str.toString(), YYYY_MM_DD_HH_MM_SS);
    }

    /**
     * 获取月最后一天
     */
    public static Date getLastDayMonth(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        // 月最后一天
        calendar.add(Calendar.MONTH, 1); // 加一个月
        calendar.set(Calendar.DATE, 1); // 设置为该月第一天
        calendar.add(Calendar.DATE, -1); // 再减一天即为上个月最后一天
        String day_last = df.format(calendar.getTime());
        StringBuffer endStr = new StringBuffer().append(day_last).append(" 23:59:59");
//        day_last = endStr.toString();
        return stringToDate(endStr.toString(), YYYY_MM_DD_HH_MM_SS);
    }

    /**
     * 获取指定日期上个月的最后一天
     *
     * @param day
     * @return
     */
    public static Date getLastDayOfLastMonth(Date day) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.set(Calendar.DAY_OF_MONTH, 0);
        String day_last = df.format(calendar.getTime());
        StringBuffer endStr = new StringBuffer().append(day_last).append(" 23:59:59");
        return stringToDate(endStr.toString(), YYYY_MM_DD_HH_MM_SS);
    }

    public static boolean isSameDate(Date date1, Date date2) {
        return org.apache.commons.lang3.time.DateUtils.isSameDay(date1, date2);
    }

    public static Date cleanHMS(Date date) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String s = sdf.format(date);
        return sdf.parse(s);
    }

    /**
     * 取time的最大毫秒值
     *
     * @param time 1586509311530
     * @return 1586509311999
     */
    public static Long getTimeMaxMillis(Long time) {
        if (time == null) return null;
        return time / 1000 * 1000 + 999;
    }

    /**
     * 获取是某年的第几周
     */
    public static int getWeekNumOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

    /**
     * 获取当前时间到第二天的秒数
     */
    public static Long getTillTomorrowSeconds(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);  //加一天
        Date tommorrow = org.apache.commons.lang3.time.DateUtils.truncate(calendar.getTime(), Calendar.DATE);//初始化第二天的时间
        System.out.println(tommorrow);
        return (tommorrow.getTime() - date.getTime()) / 1000L;
    }
}