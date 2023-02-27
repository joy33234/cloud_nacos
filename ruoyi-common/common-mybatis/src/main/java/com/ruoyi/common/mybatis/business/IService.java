//package com.ruoyi.common.mybatis.business;
//
//
//import java.util.List;
//import org.apache.ibatis.session.RowBounds;
//import tk.mybatis.mapper.entity.Condition;
//
//
//public interface IService<T> {
//    void save(T paramT);
//
//    void save(List<T> paramList);
//
//    void deleteById(Object paramObject);
//
//    void deleteByIds(String paramString);
//
//    void updateByPrimaryKeySelective(T paramT);
//
//    void updateByConditionSelective(T paramT, Condition paramCondition);
//
//    int updateByPrimaryKey(T paramT);
//
//    T findById(Object paramObject);
//
//    T findBy(String paramString, Object paramObject);
//
//    List<T> findAllBy(String paramString, Object paramObject);
//
//    List<T> findByIds(String paramString);
//
//    List<T> findByCondition(Condition paramCondition);
//
//    List<T> findByCondition(Condition paramCondition, RowBounds paramRowBounds);
//
//    List<T> findAll();
//}