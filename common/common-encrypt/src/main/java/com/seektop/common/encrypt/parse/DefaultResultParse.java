package com.seektop.common.encrypt.parse;


import com.github.pagehelper.PageInfo;
import com.seektop.common.rest.Result;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;

public class DefaultResultParse implements ResultParse {
    @Override
    public Object parse(Object source, Map encrypts) {
        Object proceedResult = source;
        if(source instanceof Result){
            proceedResult = ((Result) source).getData();
        }
        if (proceedResult instanceof PageInfo) {
            PageInfo result = (PageInfo) proceedResult;
            if(ObjectUtils.isEmpty(result.getList())){
                return source;
            }
            result.getList().forEach(v->{
                encryptObject(encrypts, v);
            });
        }else if(proceedResult instanceof List){
            List result = (List) proceedResult;
            result.forEach(v->{
                encryptObject(encrypts, v);
            });
        }else {
            /**
             * 对result包裹的对象脱敏
             */
            encryptObject(encrypts,proceedResult);
        }
        return source;
    }
}
