package com.seektop.common.mongo.utils;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoFileIdUtils {
    /**
     * @param id
     * @return
     */
    public static String getReallyId(String id){
        log.debug("原路径:->{}",id);
        if(id.contains(".")){
            return id.split("\\.")[0];
        }else {
            return id;
        }
    }
    public static String getIdFromPath(String path){
        String id = path;
        if(path.contains("/")){
            id = path.substring(path.lastIndexOf("/")+1);
        }
        if(path.contains(".")){
            id = getReallyId(id);
        }
        return id;
    }

}
