package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.model.enums.KnowledgeFileStatus;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能对话 Agent 实现
 *
 * Agent 架构（两阶段流水线）:
 *
 * ╔══════════════════════════════════════════════════════╗
 * ║              离线阶段 (Knowledge Upload)              ║
 * ║                                                      ║
 * ║  文档加载 → Chunking(切分) → Embedding(向量化) → 入库  ║
 * ║  Tika/Tika        600字/块    DashScope        pgvector║
 * ╚══════════════════════════════════════════════════════╝
 *                          ↓
 * ╔══════════════════════════════════════════════════════╗
 * ║              在线阶段 (Chat Query)                    ║
 * ║                                                      ║
 * ║  Query处理 → 粗排检索 → Rerank精排 → 答案生成         ║
 * ║  语义重写    pgvector    LLM评分     DashScope         ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * 在线阶段三步：
 * 1. Intent + Query Rewrite: 意图分析 + 语义重写
 * 2. Coarse Retrieve + Rerank: pgvector 向量相似度搜索 Top-20 → LLM 精排 Top-5
 * 3. Generate: 组装多源信息 → DashScope 生成答案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentServiceImpl implements ChatAgentService {

    private final ChatClient configChatClient;
    private final RagOnlineService ragOnlineService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final ChatHistoryService chatHistoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String chat(String question, List<Long> knowledgeFileIds) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("问题不能为空");
        }

        try {
            // ========== Step 1: 意图分析 ==========
            AgentIntent intent = analyzeIntent(question);
            log.info("Agent 意图识别: {}", intent);

            // ========== Step 2: RAG 在线检索 (粗排 + 精排) ==========
            List<Document> rerankedDocs = ragOnlineService.retrieve(question, knowledgeFileIds);
            log.info("RAG 精排后得到 {} 个相关片段", rerankedDocs.size());

            // ========== Step 3: 答案生成 ==========
            String answer = generateAnswer(question, rerankedDocs, intent);

            // ========== Step 4: 保存对话历史 ==========
            saveHistory(question, answer, knowledgeFileIds);

            log.info("Agent 对话完成: questionLen={}, answerLen={}",
                    question.length(), answer.length());
            return answer;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent 对话失败: {}", question, e);
            throw new BusinessException("对话失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String question, List<Long> knowledgeFileIds) {
        if (question == null || question.trim().isEmpty()) {
            return Flux.error(new BusinessException("问题不能为空"));
        }

        try {
            // ========== Step 1: 意图分析 ==========
            AgentIntent intent = analyzeIntent(question);
            log.info("Agent 意图识别: {}", intent);

            // ========== Step 2: RAG 在线检索 (粗排 + 精排) ==========
            List<Document> rerankedDocs = ragOnlineService.retrieve(question, knowledgeFileIds);
            log.info("RAG 精排后得到 {} 个相关片段", rerankedDocs.size());

            // ========== Step 3: 流式答案生成 ==========
            return generateAnswerStream(question, rerankedDocs, intent);

        } catch (BusinessException e) {
            return Flux.error(e);
        } catch (Exception e) {
            log.error("Agent 对话失败: {}", question, e);
            return Flux.error(new BusinessException("对话失败: " + e.getMessage()));
        }
    }

    @Override
    public List<KnowledgeFileVO> listAvailableKnowledge() {
        List<KnowledgeFileVO> all = knowledgeGraphService.pageKnowledge(1, 10000).getRecords();
        return all.stream()
                .filter(kf -> KnowledgeFileStatus.done.equals(kf.getStatus()))
                .collect(Collectors.toList());
    }

    // ==================== Step 1: 意图分析 ====================

    private AgentIntent analyzeIntent(String question) {
        String lower = question.toLowerCase();
        if (lower.contains("牛王") || lower.contains("斗牛") || lower.contains("角力")
                || lower.contains("牛角") || lower.contains("牛头") || lower.contains("牛旋")) {
            return AgentIntent.BULL_KING;
        }
        if (lower.contains("人脸") || lower.contains("注册") || lower.contains("登录")
                || lower.contains("匹配") || lower.contains("识别")) {
            return AgentIntent.FACE_RECOGNITION;
        }
        if (lower.contains("知识") || lower.contains("图谱") || lower.contains("文件")
                || lower.contains("上传") || lower.contains("文档")) {
            return AgentIntent.KNOWLEDGE_GRAPH;
        }
        return AgentIntent.KNOWLEDGE_RETRIEVAL;
    }

    // ==================== Step 3: 答案生成 ====================

    private String generateAnswer(String question, List<Document> rerankedDocs, AgentIntent intent) {
        String systemPrompt = buildSystemPrompt(intent);
        String ragContext = formatRagContext(rerankedDocs);

        String intentContext = switch (intent) {
            case BULL_KING -> "你是牛王识别专家";
            case FACE_RECOGNITION -> "你是人脸识别系统专家";
            case KNOWLEDGE_GRAPH -> "你是知识图谱管理专家";
            default -> "你是专业智能助手";
        };

        String prompt = String.format("""
                %s

                %s，请回答以下问题。

                请严格遵循以下回答规则：
                1. 只根据下方提供的【参考资料】回答用户问题
                2. 如果参考资料中有相关信息，请给出准确、详细的回答，并在回答末尾标注参考来源
                3. 如果参考资料中没有相关信息，请明确告知用户"根据现有资料无法回答该问题"
                4. 不要编造资料中不存在的信息
                5. 回答请使用中文，条理清晰，必要时使用分点说明

                === 参考资料 ===

                【RAG 向量检索结果】（按相关性排序）
                %s

                === 用户问题 ===

                %s

                请回答：""", systemPrompt, intentContext, ragContext, question);

        return configChatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 流式答案生成
     */
    private Flux<String> generateAnswerStream(String question, List<Document> rerankedDocs, AgentIntent intent) {
        String systemPrompt = buildSystemPrompt(intent);
        String ragContext = formatRagContext(rerankedDocs);

        String intentContext = switch (intent) {
            case BULL_KING -> "你是牛王识别专家";
            case FACE_RECOGNITION -> "你是人脸识别系统专家";
            case KNOWLEDGE_GRAPH -> "你是知识图谱管理专家";
            default -> "你是专业智能助手";
        };

        String prompt = String.format("""
                %s

                %s，请回答以下问题。

                请严格遵循以下回答规则：
                1. 只根据下方提供的【参考资料】回答用户问题
                2. 如果参考资料中有相关信息，请给出准确、详细的回答，并在回答末尾标注参考来源
                3. 如果参考资料中没有相关信息，请明确告知用户"根据现有资料无法回答该问题"
                4. 不要编造资料中不存在的信息
                5. 回答请使用中文，条理清晰，必要时使用分点说明
                6. 存在多段内容时候，请清晰分段换行输出，次分明逻辑清晰
                7. 有重叠内容时候，请正确合并

                === 参考资料 ===

                【RAG 向量检索结果】（按相关性排序）
                %s

                === 用户问题 ===

                %s

                请回答：""", systemPrompt, intentContext, ragContext, question);

        return configChatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    private String buildSystemPrompt(AgentIntent intent) {
        return switch (intent) {
            case BULL_KING -> "你是一个牛王识别专家，擅长分析牛王的特征、战绩和匹配结果。";
            case FACE_RECOGNITION -> "你是一个人脸识别系统专家，擅长解答关于人脸注册、登录和匹配的问题。";
            case KNOWLEDGE_GRAPH -> "你是一个知识图谱管理专家，擅长解答关于知识文件、文档处理和向量检索的问题。";
            default -> "你是一个专业的智能助手，基于知识库内容回答用户问题。";
        };
    }

    /**
     * 格式化 RAG 检索结果为文本
     */
    private String formatRagContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "（未检索到相关向量知识）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append(String.format("\n片段 %d:", i + 1));

            // 来源信息
            if (doc.getMetadata() != null) {
                Object sourceId = doc.getMetadata().get("source_id");
                Object chunkIdx = doc.getMetadata().get("chunk_index");
                if (sourceId != null) {
                    sb.append(" [来源文件ID:").append(sourceId).append("]");
                }
                if (chunkIdx != null) {
                    sb.append(" (第").append(chunkIdx).append("段)");
                }
            }

            sb.append("\n").append(truncate(doc.getText(), 800)).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(更多内容省略)";
    }

    private void saveHistory(String question, String answer, List<Long> knowledgeFileIds) {
        String idsStr = (knowledgeFileIds != null && !knowledgeFileIds.isEmpty())
                ? knowledgeFileIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                : "";
        chatHistoryService.saveChatHistory(question, answer, idsStr);
    }

    /** Agent 意图枚举 */
    enum AgentIntent {
        BULL_KING,
        FACE_RECOGNITION,
        KNOWLEDGE_GRAPH,
        KNOWLEDGE_RETRIEVAL
    }
}
