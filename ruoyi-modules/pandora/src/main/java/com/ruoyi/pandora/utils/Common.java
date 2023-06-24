package com.ruoyi.pandora.utils;

import com.ruoyi.system.api.RemoteFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class Common {

    @Autowired
    private static RemoteFileService remoteFileService;

    public static List<String> download(List<String> urlStringList,String baseUrl) {
        List<String> list = new LinkedList<>();
        try {
            String savePath = "/web/images/download/";
            OutputStream bos = null;
            InputStream is = null;
            for (String urlStr : urlStringList) {
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
                list.add(baseUrl + "/download/" + fileName);
            }
            bos.close(); // 关闭输出流
            is.close(); // 关闭输入流
        } catch (Exception e) {
            log.error("download error :{}" ,e);
        }
        return list;
    }

}
