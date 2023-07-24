package com.seektop.common.executor;

import com.seektop.common.function.NormalFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AsyncExecutor {

    @Async
    public void async(NormalFunction function){
        try {
            function.execute();
        }catch (Exception e){
            log.error("异步执行出现错误："+Thread.currentThread().getName(),e);
        }
    }
}
