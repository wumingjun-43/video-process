package com.niuwang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.niuwang.mapper.ChatHistoryMapper;
import com.niuwang.model.entity.ChatHistory;
import com.niuwang.model.vo.ChatHistoryVO;
import com.niuwang.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史服务实现
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveChatHistory(String question, String answer, String knowledgeFileIds) {
        ChatHistory history = new ChatHistory();
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setKnowledgeFileIds(knowledgeFileIds);
        save(history);
        return history.getId();
    }

    @Override
    public List<ChatHistoryVO> listRecent(int limit) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChatHistory::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<ChatHistory> list = list(wrapper);
        return list.stream().map(h -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setId(h.getId());
            vo.setQuestion(h.getQuestion());
            vo.setAnswer(h.getAnswer());
            vo.setKnowledgeFileIds(h.getKnowledgeFileIds());
            vo.setCreateTime(h.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
