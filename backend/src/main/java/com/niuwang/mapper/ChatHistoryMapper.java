package com.niuwang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuwang.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话历史 Mapper
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
