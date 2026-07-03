package com.niuwang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuwang.model.entity.MatchRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 匹配记录 Mapper 接口
 */
@Mapper
public interface MatchRecordMapper extends BaseMapper<MatchRecord> {
}
