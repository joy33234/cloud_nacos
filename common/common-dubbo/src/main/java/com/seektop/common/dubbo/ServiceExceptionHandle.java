package com.seektop.common.dubbo;

import com.google.common.base.Throwables;
import com.seektop.common.dubbo.excepton.DubboException;
import com.seektop.common.rest.rpc.RPCResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintViolationException;

/**
 * AOP处理器
 *
 * 负责处理Dubbo服务的异常情况返回内容
 */
@Slf4j
@Aspect
@Component
public class ServiceExceptionHandle {

    /**
     * Service拦截点抓取
     */
    @Pointcut(value = "execution(public com.seektop.common.rest.rpc.RPCResponse com.seektop.*.service.impl.*Service*.*(..))")
    private void servicePointcut() {

    }

    /**
     * 任何持有@Transactional注解的方法
     */
    @Pointcut(value = "@annotation(org.springframework.transaction.annotation.Transactional)")
    private void transactionalPointcut() {

    }

    /**
     * 异常处理切面(只处理非Transactional方法)
     * 异常包装为RPCResponse，避免dubbo进行包装
     *
     * @param pjp
     * @return
     */
    @Around("servicePointcut() && !transactionalPointcut()")
    public Object doAround(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        try {
            return pjp.proceed();
        } catch (DubboException e) {
            return RPCResponse.newBuilder().serverError().setMessage(processException(pjp, args, e)).build();
        } catch (ConstraintViolationException e) {
            return RPCResponse.newBuilder().paramError().setMessage(processException(pjp, args, e)).build();
        } catch (Exception e) {
            return RPCResponse.newBuilder().fail().setMessage(processException(pjp, args, e)).build();
        } catch (Throwable throwable) {
            return RPCResponse.newBuilder().fail().setMessage(processException(pjp, args, throwable)).build();
        }
    }

    /**
     * 任何持有@Transactional注解的方法异常处理切面
     * 将自定义的业务异常转为RuntimeException:
     * 1.规避dubbo的包装，让customer可以正常获取message
     * 2.抛出RuntimeException使事务可以正确回滚
     * 其他异常不处理
     *
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("servicePointcut() && transactionalPointcut()")
    public Object doTransactionalAround(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (DubboException | ConstraintViolationException e) {
            // dubbo会将异常捕获进行打印，这里就不打印了
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * 处理异常
     *
     * @param joinPoint
     * @param args
     * @param throwable
     */
    private String processException(final ProceedingJoinPoint joinPoint, final Object[] args, Throwable throwable) {
        // 组装请求参数
        String argsContent = null;
        if (args != null && args.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                sb.append(",");
                sb.append(arg);
            }
            argsContent = sb.toString().substring(1);
        }
        // 组装返回异常消息
        StringBuffer msg = new StringBuffer();
        msg.append("\n 方法: ").append(joinPoint.toLongString());
        msg.append("\n 参数: ").append(StringUtils.isEmpty(argsContent) ? "" : argsContent);
        msg.append("\n 异常: ").append(Throwables.getStackTraceAsString(throwable));
        log.error(msg.toString());
        return msg.toString();
    }

}