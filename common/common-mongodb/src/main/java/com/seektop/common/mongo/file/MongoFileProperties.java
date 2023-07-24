package com.seektop.common.mongo.file;

import com.seektop.constant.OSType;
import com.seektop.constant.ProjectConstant;
import lombok.*;
import org.bson.Document;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Field;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MongoFileProperties {

    /**
     * 如果web端可以获取到refer
     */
    private String refer;

    /**
     * 设备类型
     * @see  OSType
     */
    private Integer deviceType;

    /**
     * 应用类型
     * @see ProjectConstant.APPType
     */
    private Integer appType;


    /**
     * 是否只能后台访问【暂时无效】
     */
    private boolean backend;

    private Short features;

    public Document toDocument(){
        Document document = new Document();
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String keyName = field.getName();
            Object value = null;
            try {
                value = field.get(this);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            if (!ObjectUtils.isEmpty(value))
            document.put(keyName, value);
        }
        return document;
    }

}
