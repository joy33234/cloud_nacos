package com.seektop.common.executor;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.executor.dto.ExceptionDTO;
import com.seektop.common.function.CommonConsumer;
import com.seektop.common.function.CommonFunction;
import com.seektop.common.function.NormalConsumer;
import com.seektop.common.function.NormalSupplier;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class BatchExecutor {
    public static <T> void batchExecutor(NormalSupplier<Collection<T>> collection, NormalConsumer<T> normalFunction) throws GlobalException {
        for (T t : collection.execute()) {
            normalFunction.execute(t);
        }
    }

    public static  <T> void batchExecute(Collection<T> collection, CommonFunction<T, String> getId, CommonConsumer<T> executor) throws GlobalException {
        List<ExceptionDTO> exceptionDTOS = new ArrayList<>();
        collection.forEach(item -> {
            try {
                executor.execute(item);
            } catch (Exception e) {
                log.error("批量操作出现异常:{}-->{}", item, e);
                if (e instanceof GlobalException) {
                    exceptionDTOS.add(ExceptionDTO.builder()
                            .errorData(((GlobalException) e).getExtraMessage())
                            .id(getId.execute(item))
                            .build());
                } else {
                    exceptionDTOS.add(ExceptionDTO.builder()
                            .errorData(e.getMessage())
                            .id(getId.execute(item))
                            .build());
                }
            }
        });
        if (exceptionDTOS.size() > 1) {
            throw new GlobalException(ResultCode.SERVER_ERROR, JSONObject.toJSONString(exceptionDTOS));
        }
    }
}

