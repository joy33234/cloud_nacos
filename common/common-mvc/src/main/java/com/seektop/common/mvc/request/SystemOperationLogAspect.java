package com.seektop.common.mvc.request;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.MenuApiManageDO;
import com.seektop.report.system.GlSystemOperationLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@Slf4j
public class SystemOperationLogAspect {

    @Resource
    private RedisService redisService;
    @Resource
    private ReportService reportService;

    @Pointcut(value = "execution(* com.seektop.*.controller.backend.*.*(..))")
    public void backendPointCut(){}

    @Pointcut(value = "@annotation(com.seektop.common.mvc.aop.IgnoreOperationLog)")
    public void ignorePointCut(){}

    @Pointcut(value = "@annotation(org.springframework.web.bind.annotation.ModelAttribute)")
    public void ignorePointCut2(){}

    @Pointcut(value = "@annotation(org.springframework.web.bind.annotation.InitBinder)")
    public void ignorePointCut3(){}

    @Pointcut("backendPointCut() && !ignorePointCut() && !ignorePointCut2() && !ignorePointCut3() ")
    public void pointCut(){}

    @Around("pointCut()")
    public Object after(ProceedingJoinPoint pjp) throws Throwable {
        Object result = null; // 定义返回参数
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        // 请求的URL
        String requestURL = request.getRequestURI();
        //判断是否需要记录日志
       /* if (!isRecord(requestURL)){
            return pjp.proceed(); // 动态代理执行目标方法
        }*/
        // 获取当前用户
        String uid = request.getHeader("uid");
        if (StringUtils.isBlank(uid)) {
            return pjp.proceed(); // 动态代理执行目标方法
        }
        //菜单id
        String menuId = request.getHeader("menuId");
        if (StringUtils.isBlank(menuId)){
            return pjp.proceed(); // 动态代理执行目标方法
        }
        GlAdminDO adminDO = redisService.get(KeyConstant.TOKEN.ADMIN_USER + uid, GlAdminDO.class);
        if (ObjectUtils.isEmpty(adminDO)) {
            return pjp.proceed(); // 动态代理执行目标方法
        }
        // 获取请求参数
        String[] parameterNamesArgs = ((MethodSignature) pjp.getSignature()).getParameterNames(); // 获取参数名称数组
        Object[] args = pjp.getArgs(); // 获取方法参数

        result = pjp.proceed();// 动态代理执行目标方法
        //获取返回值
        try{
            //上报 mq 数据格式
            String opeartionMenu = request.getHeader("opeartionMenu");
            if (StringUtils.isNotBlank(opeartionMenu)){
                opeartionMenu = this.unescape(opeartionMenu);
            }
            //返回值
            GlSystemOperationLogEvent logEvent = GlSystemOperationLogEvent.builder()
                    .adminId(adminDO.getUserId())
                    .jobId(adminDO.getJobId())
                    .operationTime(System.currentTimeMillis())
                    .url(requestURL)
                    .operationMenu(opeartionMenu)
                    .menuId(menuId)
                    .operator(adminDO.getUsername())
                    .param(this.getParamStr(parameterNamesArgs,args))
                    .result(this.getResultStr(result))
                    .build();
            // MQ 推送记录日志数据
            reportService.systemOperationReport(logEvent);
            log.debug("操作日志debug,url={}",requestURL);
        }catch (Exception e){
            log.debug("操作日志记录错误! url,e ={}",requestURL,e);
        }
        return result;
    }

    /**
     * 判断这个url是否是操作类型的url
     * @param url
     * @return true:是,需要记录  false:否
     */
    private Boolean isRecord(String url){
        //  根据url 找 gl_menu_api_manage 表中 type = 2 的数据
        List<MenuApiManageDO> menuApiManageDOList = new ArrayList<>();
        RedisResult<MenuApiManageDO> systemManageResult = redisService.getListResult(KeyConstant.ADMIN_AUTH.MENU_API_MANAGE_CACHE, MenuApiManageDO.class);
        if (systemManageResult.isExist()){
            menuApiManageDOList = systemManageResult.getListResult();
        }else{
            return Boolean.FALSE;
        }
        return menuApiManageDOList.stream().filter(t -> t.getType() > 1 && t.getUrl().equals(url)).count() > 0 ;
    }

    /**
     * 前端中文解密
     * @param src
     * @return
     */
    private String unescape(String src) {
        StringBuffer tmp = new StringBuffer();
        try{
            tmp.ensureCapacity(src.length());
            int lastPos = 0, pos = 0;
            char ch;
            while (lastPos < src.length()) {
                pos = src.indexOf("%", lastPos);
                if (pos == lastPos) {
                    if (src.charAt(pos + 1) == 'u') {
                        ch = (char) Integer.parseInt(src
                                .substring(pos + 2, pos + 6), 16);
                        tmp.append(ch);
                        lastPos = pos + 6;
                    } else {
                        ch = (char) Integer.parseInt(src
                                .substring(pos + 1, pos + 3), 16);
                        tmp.append(ch);
                        lastPos = pos + 3;
                    }
                } else {
                    if (pos == -1) {
                        tmp.append(src.substring(lastPos));
                        lastPos = src.length();
                    } else {
                        tmp.append(src.substring(lastPos, pos));
                        lastPos = pos;
                    }
                }
            }
        }catch (Exception e){

        }
        return tmp.toString();
    }

    private String getResultStr(Object obj){
        if (obj instanceof Result){
            Result res = (Result) obj;
            Result resultStr = new Result();
            resultStr.setCode(res.getCode());
            resultStr.setMessage(res.getMessage());
            return JSONObject.toJSONString(resultStr);
        }else{
           return null;
        }
    }

    private String getParamStr(String[] parameterNamesArgs,Object[] args){
        JSONObject param= new JSONObject();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest || args[i] instanceof ServletResponse || args[i] instanceof MultipartFile) {
                //ServletRequest不能序列化，从入参里排除，否则报异常：java.lang.IllegalStateException: It is illegal to call this method if the current request is not in asynchronous mode (i.e. isAsyncStarted() returns false)
                //ServletResponse不能序列化 从入参里排除，否则报异常：java.lang.IllegalStateException: getOutputStream() has already been called for this response
                continue;
            }
            if (args[i] instanceof GlAdminDO){
                continue;
            }
            if (args[i] instanceof BindingResult){
                continue;
            }
            param.put(parameterNamesArgs[i],args[i]);
        }
        try{
           return param.toString();
        }catch (Exception e){
           return getParamStr2(parameterNamesArgs,args);
        }
    }

    private String getParamStr2(String[] parameterNamesArgs,Object[] args){
        StringBuffer paramsBuf = new StringBuffer();
        // 获取请求参数集合并进行遍历拼接
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest || args[i] instanceof ServletResponse || args[i] instanceof MultipartFile) {
                //ServletRequest不能序列化，从入参里排除，否则报异常：java.lang.IllegalStateException: It is illegal to call this method if the current request is not in asynchronous mode (i.e. isAsyncStarted() returns false)
                //ServletResponse不能序列化 从入参里排除，否则报异常：java.lang.IllegalStateException: getOutputStream() has already been called for this response
                continue;
            }
            if (args[i] instanceof GlAdminDO){
                continue;
            }
            if (args[i] instanceof BindingResult){
                continue;
            }
            if (paramsBuf.length() > 0) {
                paramsBuf.append("|");
            }
            paramsBuf.append(parameterNamesArgs[i]).append(" = ").append(args[i]);
        }
        return paramsBuf.toString();
    }
}
