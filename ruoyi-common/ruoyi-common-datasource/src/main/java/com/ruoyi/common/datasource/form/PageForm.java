package com.ruoyi.common.datasource.form;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface PageForm {
    int getStart();

    void setStart(int paramInt);

    int getLimit();

    void setLimit(int paramInt);

    String getSort();

    void setSort(String paramString);

    String getDirection();

    void setDirection(String paramString);

    int getPage();

    void setPage(int paramInt);

    default IPage converterIPage() {
        Page page = new Page(getPage(), getLimit());
        if (StringUtils.isNotBlank(getSort()))
            if (getDirection().equalsIgnoreCase("desc")) {
                page.addOrder(OrderItem.descs(getSort().split(",")));
            } else {
                page.addOrder(OrderItem.ascs(getSort().split(",")));
            }
        return (IPage)page;
    }

    default PageForm next() {
        setPage(getPage() + 1);
        return this;
    }
}
