package com.seektop.fund.payment.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * groovy脚本执行工具。
 * 每个支付商户id对应一个类加载器，每个支付商户的类加载器加载自己的groovy脚本
 * 所有类加载器的父类加载器Spring的类加载器
 * 每次加载脚本时，先对脚本做md5摘要，与缓存的脚本对象md5比较，一致的直接执行，不一致的重新加载
 * TODO 类加载时的线程安全问题需要解决,首次加载时可能出现性能问题，不会影响数据正确性，延后处理
 */
@Slf4j
public class GroovyScriptUtil {

    // groovy脚本对应的类对象
    public static ConcurrentHashMap<Integer, GroovyScriptObject> passedClassMap = new ConcurrentHashMap<>();
    // 没有支付商户id对应一个类加载器
    public static ConcurrentHashMap<Integer, GroovyClassLoader> groovyClassLoaderMap = new ConcurrentHashMap<>();

    /**
     * 解析groovy脚本，执行方法
     *
     * @param id     入款商户id 作为唯一标识符
     * @param script 实际脚本原文
     * @param fun    要调用的方法名 @code{GroovyFunctionEnum}
     * @param args   调用方法传入的实际参数
     * @return 方法执行结果
     */
    public static Object invokeMethod(Integer id, String script, String scriptSign, GroovyFunctionEnum fun, Object[] args) {
        // 获取脚本对应实例对象
        GroovyObject groovy = getGroovyObject(id, script, scriptSign);
        if (groovy != null) {
            // 传递方法名和参数，执行方法
            return groovy.invokeMethod(fun.getFunctionName(), args);
        } else {
            return null;
        }
    }

    private static GroovyObject getGroovyObject(Integer id, String script, String md5Digest) {
        // 获取缓存中groovy脚本对应实例对象
        GroovyScriptObject groovyScriptObject = passedClassMap.get(id);
        // 如果缓存没有实例对象，创建一个实例对象并新增缓存
        if (Objects.isNull(groovyScriptObject)) {
            groovyScriptObject = new GroovyScriptObject();
            groovyScriptObject.setGroovyObject(parseScript(id, script));
            groovyScriptObject.setMd5Digest(md5Digest);
            groovyScriptObject.setScript(script);
            groovyScriptObject.setId(id);
            passedClassMap.put(id, groovyScriptObject);
        } else {
            // 如果新脚本和缓存脚本的md5摘要不一致，创建一个实例对象并更新缓存
            if (!StringUtils.equals(md5Digest, groovyScriptObject.getMd5Digest())) {
                groovyScriptObject.setGroovyObject(parseScript(id, script));
                groovyScriptObject.setMd5Digest(md5Digest);
                groovyScriptObject.setScript(script);
                groovyScriptObject.setId(id);
                passedClassMap.putIfAbsent(id, groovyScriptObject);
            }
        }
        return groovyScriptObject.getGroovyObject();
    }

    private static GroovyObject parseScript(Integer id, String script) {
        String fullScript = script;
        GroovyObject groovyObject = null;
        try {
            Class groovyClass = getClassLoader(id).parseClass(fullScript);
            groovyObject = (GroovyObject) groovyClass.newInstance();
        } catch (Exception e) {
            log.error("加载groovy脚本异常 \n{} \n{} \n", script, e.getMessage(), e);
        }
        return groovyObject;

    }

    /**
     * 从缓存中获取id对应的类加载器，若不存在，新增一个
     *
     * @param id
     * @return
     */
    private static GroovyClassLoader getClassLoader(Integer id) {
        if (groovyClassLoaderMap.contains(id)) {
            return groovyClassLoaderMap.get(id);
        } else {
            // 获取appClassLoader作为父类加载器
            ClassLoader parent = GroovyScriptUtil.class.getClassLoader();
            GroovyClassLoader groovyClassLoader = new GroovyClassLoader(parent);
            groovyClassLoaderMap.put(id, groovyClassLoader);
            return groovyClassLoader;
        }
    }

    private static final Integer validateId = -1;

    public static boolean validateScript(Integer id, String script, String md5Digest) {
        passedClassMap.remove(validateId);
        GroovyObject groovyObject = getGroovyObject(id, script, md5Digest);
        return Objects.nonNull(groovyObject);
    }

    @Data
    static class GroovyScriptObject {
        private String script;
        private String md5Digest;
        private Integer id;
        private GroovyObject groovyObject;
    }
}
