package com.seektop.common.dubbo.filter;

import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.common.utils.NumStringUtils;
import com.seektop.common.utils.UserIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;

@Slf4j
@Activate(order = Integer.MAX_VALUE, group = {PROVIDER, CONSUMER})
public class LogTraceFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (RpcContext.getContext().isConsumerSide()) {
            // 如果是消费者从当前线程中获取traceId然后放到RpcContext
            String traceId = MDC.get("traceId");
            if (StringUtils.isEmpty(traceId)) {
                traceId = NumStringUtils.getUUID();
            }
            RpcContext.getContext().setAttachment("traceId", traceId);
            // 如果是消费者从当前线程中获取userId然后放到RpcContext
            String userId = MDC.get("userId");
            if (StringUtils.hasText(userId)) {
                RpcContext.getContext().setAttachment("userId", userId);
            }
            return invoker.invoke(invocation);
        }
        if (RpcContext.getContext().isProviderSide()) {
            // 如果是生产者从RpcContext中获取traceId，然后放入到当前线程中等待后续log输出使用
            String traceId = RpcContext.getContext().getAttachment("traceId");
            if (StringUtils.hasText(traceId)) {
                MDC.put("traceId", traceId);
            }
            // 如果是生产者从RpcContext中获取userId，然后放入到当前线程中等待后续log输出使用
            String userId = RpcContext.getContext().getAttachment("userId");
            if (StringUtils.hasText(userId)) {
                MDC.put("userId", userId);
                UserIdUtils.setUserId(Integer.valueOf(userId));
            }
            try {
                return invoker.invoke(invocation);
            } finally {
                // dubbo调用提供者需要清除
                JobEncryptPermissionUtils.release();
            }
        }
        return invoker.invoke(invocation);
    }

}