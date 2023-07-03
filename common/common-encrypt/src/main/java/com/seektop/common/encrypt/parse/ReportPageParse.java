package com.seektop.common.encrypt.parse;

import com.seektop.common.rest.Result;
import com.seektop.common.rest.data.ReportPageResult;
import org.springframework.util.ObjectUtils;

import java.util.Map;

public class ReportPageParse implements ResultParse {
    @Override
    public Object parse(Object source, Map encrypts) {
        Object proceedResult = source;
        if (source instanceof Result) {
            proceedResult = ((Result) source).getData();
        }
        if (proceedResult instanceof ReportPageResult) {
            ReportPageResult result = (ReportPageResult) proceedResult;
            if (ObjectUtils.isEmpty(result.getList())) {
                return source;
            }
            result.getList().forEach(v -> {
                encryptObject(encrypts, v);
            });
        }
        return source;
    }
}
