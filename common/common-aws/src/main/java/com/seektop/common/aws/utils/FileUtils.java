package com.seektop.common.aws.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class FileUtils {

    /**
     * 根据文件名字判断文件类型
     */
    public static String getTypeByName(String fileName){
        if(fileName.lastIndexOf(".")!=-1) {
            return fileName.substring(fileName.lastIndexOf(".")+1);
        }else {
            return null;
        }
    }


    /**
     * 根据mime类型判断文件类型
     */
    public static String getTypeByContentType(String contentType) {
        if (StringUtils.isEmpty(contentType) || contentType.indexOf("/") < 0) return contentType;
        return contentType.substring(0, contentType.indexOf("/"));
    }

    /**
     * 根据mime类型判断文件子类型
     */
    public static String getSubType(String contentType) {
        if (StringUtils.isEmpty(contentType) || contentType.indexOf("/") < 0) return contentType;
        return contentType.substring(contentType.indexOf("/")+1);
    }
    /**
     * 获取系统临时文件夹
     */
    public static String getTempPath(){
        return System.getProperty("java.io.tmpdir")+File.separator;
    }
    /**
     * 获取系统可用内存
     */



}
