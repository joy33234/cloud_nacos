package com.ruoyi.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.file.utils.FileUploadUtils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 本地文件存储
 * 
 * @author ruoyi
 */
@Primary
@Service
@Slf4j
public class LocalSysFileServiceImpl implements ISysFileService
{
    /**
     * 资源映射路径 前缀
     */
    @Value("${file.prefix}")
    public String localFilePrefix;

    /**
     * 域名或本机访问地址
     */
    @Value("${file.domain}")
    public String domain;
    
    /**
     * 上传文件存储在本地的根路径
     */
    @Value("${file.path}")
    private String localFilePath;

    /**
     * 本地文件上传接口
     * 
     * @param file 上传的文件
     * @return 访问地址
     * @throws Exception
     */
    @Override
    public String uploadFile(MultipartFile file) throws Exception
    {
        String name = FileUploadUtils.upload(localFilePath, file);
        String url = domain + localFilePrefix + name;
        return url;
    }

    @Override
    public List<String> getDownUrl(List<String> urls) throws Exception {
        List<String> list = new LinkedList<>();
        try {
            String savePath = "/web/images/download/";
            OutputStream bos = null;
            InputStream is = null;
            for (String urlStr : urls) {
                // 创建URL对象
                URL url = new URL(urlStr);
                // 打开网络连接
                is = url.openStream();

                String fileName = UUID.randomUUID() + ".jpg";
                // 创建BufferedOutputStream对象
                bos = new BufferedOutputStream(new FileOutputStream(savePath + fileName));

                byte[] buffer = new byte[1024];
                int count = 0;
                while ((count = is.read(buffer, 0, buffer.length)) != -1) { // 从网络流中读取数据
                    bos.write(buffer, 0, count); // 写入本地文件
                }
                list.add(domain + localFilePrefix + "/download/" + fileName);
            }
            bos.close(); // 关闭输出流
            is.close(); // 关闭输入流
        } catch (Exception e) {
            log.error("download error :{}" ,e);
        }
        return list;
    }
}
