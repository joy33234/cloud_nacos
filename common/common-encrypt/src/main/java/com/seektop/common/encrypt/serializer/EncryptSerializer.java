package com.seektop.common.encrypt.serializer;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.seektop.common.encrypt.EncryptHelper;
import com.seektop.common.encrypt.annotation.EncryptField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class EncryptSerializer implements ObjectSerializer {

    @Override
    public void write(JSONSerializer jsonSerializer, Object o, Object fieldName, Type type, int i) throws IOException {

        Class<?> aClass = jsonSerializer.getContext().object.getClass();
        Field field = ReflectionUtils.findField(aClass, fieldName.toString());
        EncryptField annotation = field.getDeclaredAnnotation(EncryptField.class);
        List<Function<String, String>> parses = EncryptHelper.getParses(Arrays.asList(annotation.typeEnums()));
        SerializeWriter out = jsonSerializer.out;
        if (null == o) {
            out.writeString("");
        } else {
            if (!CollectionUtils.isEmpty(parses)) {
                String value = o.toString();
                for (Function<String,String> parse:parses) {
                  value = parse.apply(value);
                }
                out.writeString(value);
            } else {
                out.writeString(o.toString());
            }
        }

    }

}



