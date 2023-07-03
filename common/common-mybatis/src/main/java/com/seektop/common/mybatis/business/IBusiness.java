package com.seektop.common.mybatis.business;

import com.seektop.exception.GlobalException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.session.RowBounds;
import tk.mybatis.mapper.entity.Condition;

import java.util.List;

public interface IBusiness<T> {

    /**
     * 持久化
     *
     * @param model
     */
    void save(T model);

    /**
     * 批量持久化
     *
     * @param models
     */
    void save(List<T> models);

    /**
     * 通过主鍵刪除
     *
     * @param id
     */
    void deleteById(Object id);

    /**
     * 批量刪除
     *
     * eg：ids -> “1,2,3,4”
     * @param ids
     */
    void deleteByIds(String ids);

    /**
     * 更新
     *
     * @param model
     */
    void updateByPrimaryKeySelective(T model);
    /**
     * 更新
     *
     * @param model
     */
    void updateByConditionSelective(T model, Condition condition);

    /**
     * 全量更新
     *
     * @param model
     */
    int updateByPrimaryKey(T model);

    /**
     * 通过ID查找
     *
     * @param id
     * @return
     */
    T findById(Object id);

    /**
     * 通过Model中某个成员变量名称（非数据表中column的名称）查找,value需符合unique约束
     *
     * @param fieldName
     * @param value
     * @return
     * @throws TooManyResultsException
     */
    T findBy(String fieldName, Object value) throws GlobalException;

    /**
     * 通过满足熟悉条件的全部数据
     *
     * @param fieldName
     * @param value
     * @return
     * @throws GlobalException
     */
    List<T> findAllBy(String fieldName, Object value) throws GlobalException;

    /**
     * 通过多个ID查找
     * eg：ids -> “1,2,3,4”
     *
     * @param ids
     * @return
     */
    List<T> findByIds(String ids);

    /**
     * 根据条件查找
     *
     * @param condition
     * @return
     */
    List<T> findByCondition(Condition condition);

    /**
     * 根据条件查找
     *
     * @param condition
     * @return
     */
    List<T> findByCondition(Condition condition, RowBounds rowBounds);

    /**
     * 获取所有
     *
     * @return
     */
    List<T> findAll();

}