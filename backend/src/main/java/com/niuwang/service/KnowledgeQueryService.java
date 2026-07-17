package com.niuwang.service;

import com.niuwang.model.vo.KnowledgeFileVO;
import java.util.List;

/**
 * 知识图谱查询服务接口
 * 从结构化知识图谱中查询精确信息
 */
public interface KnowledgeQueryService {

    /**
     * 基于关键词查询知识图谱中的结构化信息
     *
     * @param question         用户问题
     * @param knowledgeFileIds 限定知识文件范围（null/空表示全部）
     * @return 相关知识片段列表
     */
    List<String> queryKnowledgeGraph(String question, List<Long> knowledgeFileIds);

    /**
     * 获取知识文件元数据
     */
    List<KnowledgeFileVO> listKnowledgeFiles();
}
