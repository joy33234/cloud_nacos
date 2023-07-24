package com.seektop.common.nacos.adapter.base;

public interface ChangeListener {
    void onChanged(String dataId,String content);
}
