//package com.ruoyi.common.mybatis.utils;
//
//
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.google.common.collect.Lists;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.BeanUtils;
//
//import java.util.List;
//import java.util.function.Function;
//
//public class DtoUtils {
//    private static final Logger log = LoggerFactory.getLogger(DtoUtils.class);
//
//    public static <T> T transformBean(Object source, Class<T> targetClass, String... ignores) {
//        try {
//            if (source == null)
//                return null;
//            T target = targetClass.newInstance();
//            BeanUtils.copyProperties(source, target, ignores);
//            return target;
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
//    }
//
//    public static <T> List<T> transformList(List<?> source, Class<T> targetClass, String... ignores) {
//        if (source == null)
//            return null;
//        List<T> result = Lists.newArrayList();
//        source.stream().forEach(r -> result.add(transformBean(r, targetClass, ignores)));
//        return result;
//    }
//
//    public static <T, R> List<R> transformList(List<T> source, Function<T, R> function) {
//        if (source == null)
//            return null;
//        List<R> result = Lists.newArrayList();
//        source.stream().forEach(t -> result.add(function.apply(t)));
//        return result;
//    }
//
//    public static <T> Page<T> transformPageInfo(Page<?> source, Class<T> targetClass, String... ignores) {
//        if (source == null)
//            return null;
//        Page<T> result = new Page();
//        if (source.getOrders() == null) {
//            BeanUtils.copyProperties(source, result, new String[] { "list" });
//            return result;
//        }
//        List<T> list = Lists.newArrayList();
//        result.getRecords().stream().forEach(r -> list.add(transformBean(r, targetClass, ignores)));
//        result.setRecords(list);
//        return result;
//    }
//}
