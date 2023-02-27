package com.ruoyi.okx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.okx.domain.OkxCoin;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CoinMapper extends BaseMapper<OkxCoin> {

}
