package com.seektop.common.csvexport.function;


import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInfo;
import com.seektop.common.csvexport.CsvExportComponent;
import com.seektop.common.csvexport.model.Export;
import com.seektop.common.csvexport.model.FieldMap;
import com.seektop.common.csvexport.utils.CsvExportUtils;
import com.seektop.exception.GlobalException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 导出数据源类型
 * @param <R>
 */
public interface SourceFunction<R>{
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CsvExportComponent.class);
    @FunctionalInterface
    interface PageInfoQuery<R> extends SourceFunction<R> {
        PageInfo<R> apply(Integer page, Integer size) throws GlobalException;
        default void readAndWrite(OutputStream outputStream, Function<R, StringBuffer> parser) throws GlobalException,IOException {
            // 先查询一次 计算出总页数
            Export export = CsvExportUtils.getExport();
            int page = 1;
            int size = 2000;
            long totalPage;
            PageInfo<R> pageInfo;
            // apply方法 就是我们在导出数据的时候写的查询方法
            pageInfo = this.apply(1, 1);
            if(pageInfo.getTotal() == 0)return;
            size = Optional.ofNullable(export.getPageSize()).orElse(2000);
            long pages = (long) (Math.ceil(Double.valueOf(pageInfo.getTotal())/Double.valueOf(size))+1);
            int maxPages = Optional.ofNullable(export.getTotal()).orElse(200000)/size+1;
            totalPage = Math.min(pages,maxPages);//新增ES限制
            // 写内容 避免 mybatis出现 size大于本身记录数时分页无效，第一次查询不做写的操作
            log.info("导出开始，共{}页",totalPage);
            do {
                pageInfo = this.apply(page, size);
                // 数据为空，就结束
                if(ObjectUtils.isEmpty(pageInfo) || CollectionUtils.isEmpty(pageInfo.getList())){
                    break;
                }
                for (R item : pageInfo.getList()) {
                    parse(outputStream, parser, item);
                }
                log.info("导出进行中，共{}页，当前{}",totalPage,page);
                page++;
                Optional.ofNullable(export.getInterval()).ifPresent(
                        interval->{
                            try {
                                TimeUnit.MICROSECONDS.sleep(interval);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                );
            } while (page < totalPage);
        }
    }

    @FunctionalInterface
    interface ListQuery<R> extends SourceFunction<R> {
        List<R> apply() ;
        default void readAndWrite(OutputStream outputStream, Function<R, StringBuffer> parser) throws IOException {
            for (R item : this.apply()) {
                parse(outputStream, parser, item);
            }
        }
    }

    /**
     *
     * @param outputStream
     * @param parser
     * @param item
     * @param <R>
     * @throws IOException
     */
    static <R> void parse(OutputStream outputStream, Function<R, StringBuffer> parser, R item) throws IOException {
        try {
            // 我们在导出数据的时候，提供的解析数据的方法
            StringBuffer sb = parser.apply(item);
            outputStream.write(sb.toString().getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
        }catch (Exception e){
            log.info("数据转换失败：item:{},exception:{}", JSONObject.toJSONString(item),e);
            outputStream.write(("数据有误，转换失败:"+ JSONObject.toJSONString(item)).getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
        }
    }

    default PageInfo<R> apply(Integer size, Integer page) throws GlobalException {
        throw new RuntimeException("interface default function");
    }
    default List<R> apply() throws GlobalException {
        throw new RuntimeException("interface default function");
    }
    default void readAndWrite(OutputStream outputStream, Function<R, StringBuffer> parser) throws GlobalException, IOException {
        throw new RuntimeException("interface default function");
    }

    /**
     * 默认导出值初始化的方法
     * @param fieldMaps
     * @param item
     * @return
     */
    default StringBuffer commonParse(List<FieldMap> fieldMaps, R item) {
        StringBuffer stringBuffer = new StringBuffer();
        Class c = item.getClass();
        fieldMaps.forEach(v -> {
            try {
                //反射获取属性
//                Field field = c.getField(v.getFieldName());
                Field field = ReflectionUtils.findField(c,v.getFieldName());
                field.setAccessible(true);
                //获取数据
                Object o = field.get(item);
                Object result = "";
                if(!ObjectUtils.isEmpty(o)){
                    result = o.toString();
                }
                Function parse = v.getParse();
                //如果需要处理
                if (parse != null) {
                    result = parse.apply(o);
                }
                stringBuffer.append("\"").append(result.toString()).append("\"").append(",");
            } catch (Exception e) {
                stringBuffer.append("该数据转换失败").append(v).append(",");
                e.printStackTrace();
            }
        });
        return stringBuffer.deleteCharAt(stringBuffer.length()-1);
    }
    default StringBuffer functionParse(List<FieldMap> fieldMaps, R item) {
        StringBuffer stringBuffer = new StringBuffer();
        fieldMaps.forEach(v -> {
            try {
                String result = "";
                Function parse = v.getParse();
                //如果需要处理
                if (parse != null) {
                    result = Optional.ofNullable(parse.apply(item)).orElse(result).toString();
                }
                stringBuffer.append("\"").append(result).append("\"").append(",");
            } catch (Exception e) {
                stringBuffer.append("该数据转换失败").append(v).append(",");
                log.error("数据转换失败:{}",e.getStackTrace());
            }
        });
        return stringBuffer.deleteCharAt(stringBuffer.length()-1);
    }
}
