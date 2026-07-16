package com.niuwang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuwang.model.entity.ChatHistory;
import com.niuwang.model.vo.ChatHistoryVO;

import java.util.List;

/**
 * 对话历史服务接口
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /** 保存对话记录 */
    Long saveChatHistory(String question, String answer, String knowledgeFileIds);

    /** 查询最近对话历史 */
    List<ChatHistoryVO> listRecent(int limit);
}
