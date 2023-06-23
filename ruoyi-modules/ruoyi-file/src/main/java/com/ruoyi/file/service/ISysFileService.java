package com.ruoyi.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传接口
 * 
 * @author ruoyi
 */
public interface ISysFileService
{
    /**
     * 文件上传接口
     * 
     * @param file 上传的文件
     * @return 访问地址
     * @throws Exception
     */
    public String uploadFile(MultipartFile file) throws Exception;


    public List<String> getDownUrl(List<String> urls) throws Exception;
}
