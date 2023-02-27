//package com.ruoyi.common.mybatis.business;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.ParameterizedType;
//import java.util.List;
//
//import org.apache.ibatis.session.RowBounds;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Lazy;
//import tk.mybatis.mapper.entity.Condition;
//
//public class AbstractService<T> implements IService<T> {
//    @Lazy
//    @Autowired
//    protected Mapper<T> mapper;
//
//    private Class<T> modelClass;
//
//    public AbstractService() {
//        ParameterizedType pt = (ParameterizedType)getClass().getGenericSuperclass();
//        this.modelClass = (Class<T>)pt.getActualTypeArguments()[0];
//    }
//
//    public void save(T model) {
//        this.mapper.insertSelective(model);
//    }
//
//    public void save(List<T> models) {
//        this.mapper.insertList(models);
//    }
//
//    public void deleteById(Object id) {
//        this.mapper.deleteByPrimaryKey(id);
//    }
//
//    public void deleteByIds(String ids) {
//        this.mapper.deleteByIds(ids);
//    }
//
//    public void updateByPrimaryKeySelective(T model) {
//        this.mapper.updateByPrimaryKeySelective(model);
//    }
//
//    public void updateByConditionSelective(T model, Condition condition) {
//        this.mapper.updateByConditionSelective(model, condition);
//    }
//
//    public int updateByPrimaryKey(T model) {
//        return this.mapper.updateByPrimaryKey(model);
//    }
//
//    public T findById(Object id) {
//        return (T)this.mapper.selectByPrimaryKey(id);
//    }
//
//    public T findBy(String fieldName, Object value) {
//        try {
//            T model = this.modelClass.newInstance();
//            Field field = this.modelClass.getDeclaredField(fieldName);
//            field.setAccessible(true);
//            field.set(model, value);
//            return (T)this.mapper.selectOne(model);
//        } catch (ReflectiveOperationException e) {
//            throw new ServiceException(e.getMessage(), 500);
//        }
//    }
//
//    public List<T> findAllBy(String fieldName, Object value) throws ServiceException {
//        try {
//            T model = this.modelClass.newInstance();
//            Field field = this.modelClass.getDeclaredField(fieldName);
//            field.setAccessible(true);
//            field.set(model, value);
//            return this.mapper.select(model);
//        } catch (ReflectiveOperationException e) {
//            throw new ServiceException(e.getMessage(), 500);
//        }
//    }
//
//    public List<T> findByIds(String ids) {
//        return this.mapper.selectByIds(ids);
//    }
//
//    public List<T> findByCondition(Condition condition) {
//        return this.mapper.selectByCondition(condition);
//    }
//
//    public List<T> findByCondition(Condition condition, RowBounds rowBounds) {
//        return this.mapper.selectByExampleAndRowBounds(condition, rowBounds);
//    }
//
//    public List<T> findAll() {
//        return this.mapper.selectAll();
//    }
//}
