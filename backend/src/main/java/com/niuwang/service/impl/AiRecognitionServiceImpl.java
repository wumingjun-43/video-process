package com.niuwang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.niuwang.common.exception.BusinessException;
import com.niuwang.mapper.BullKingImageMapper;
import com.niuwang.mapper.BullKingMapper;
import com.niuwang.mapper.KnowledgeFileMapper;
import com.niuwang.model.dto.BullKingMatchDTO;
import com.niuwang.model.entity.BullKing;
import com.niuwang.model.entity.BullKingImage;
import com.niuwang.model.entity.KnowledgeFile;
import com.niuwang.model.vo.FaceAnalysisResult;
import com.niuwang.model.vo.FaceMatchResultVO;
import com.niuwang.model.vo.FeatureAnalysisVO;
import com.niuwang.model.vo.MatchResultVO;
import com.niuwang.service.AiRecognitionService;
import com.niuwang.service.FaceEmbeddingService;
import com.niuwang.service.MatchRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 识别服务实现类
 * 基于 spring-ai-alibaba DashScope 模型进行牛王匹配和特征分析
 */
@Service
@Slf4j
public class AiRecognitionServiceImpl implements AiRecognitionService {

    private final ChatClient chatClient;
    @org.springframework.beans.factory.annotation.Qualifier("dashScopeChatModel")
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final BullKingMapper bullKingMapper;
    private final BullKingImageMapper bullKingImageMapper;
    private final KnowledgeFileMapper knowledgeFileMapper;
    private final MatchRecordService matchRecordService;
    private final FaceEmbeddingService faceEmbeddingService;

    public AiRecognitionServiceImpl(
            ChatClient chatClient,
            @org.springframework.beans.factory.annotation.Qualifier("dashScopeChatModel") ChatModel chatModel,
            VectorStore vectorStore,
            BullKingMapper bullKingMapper,
            BullKingImageMapper bullKingImageMapper,
            KnowledgeFileMapper knowledgeFileMapper,
            MatchRecordService matchRecordService,
            FaceEmbeddingService faceEmbeddingService) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.bullKingMapper = bullKingMapper;
        this.bullKingImageMapper = bullKingImageMapper;
        this.knowledgeFileMapper = knowledgeFileMapper;
        this.matchRecordService = matchRecordService;
        this.faceEmbeddingService = faceEmbeddingService;
    }


    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.access-url}")
    private String accessUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchResultVO matchBullKing(BullKingMatchDTO dto) {
        try {
            MultipartFile imageFile = dto.getImage();
            byte[] imageBytes = imageFile.getBytes();
            String mimeType = imageFile.getContentType() != null ? imageFile.getContentType() : "image/jpeg";
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

            List<BullKing> allBulls = bullKingMapper.selectList(
                    new LambdaQueryWrapper<BullKing>().orderByDesc(BullKing::getCreateTime).last("LIMIT 20"));

            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个牛王图像识别专家。分析此图片并与已知牛王进行匹配。\n\n");
            prompt.append("Known bull kings in database:\n");
            for (int i = 0; i < allBulls.size(); i++) {
                BullKing bk = allBulls.get(i);
                prompt.append((i + 1)).append(". ID=").append(bk.getId())
                        .append(", desc=").append(bk.getDescription() != null ? bk.getDescription() : "none")
                        .append(", record=").append(bk.getBattleRecord() != null ? bk.getBattleRecord() : "none")
                        .append("\n");
            }
            prompt.append("\nAnalyze the image features: horn, head, eye, fur, swirl, leg.\n");
            prompt.append("Then find the best matching bull king from the list.\n");
            prompt.append("Return ONLY a JSON object like this: {\"bullKingId\": 1, \"confidence\": 0.95, \"aiAnalysis\": \"brief analysis\", \"features\": {\"horn\": \"...\", \"head\": \"...\", \"eye\": \"...\", \"fur\": \"...\", \"swirl\": \"...\", \"leg\": \"...\"}}\n");
            prompt.append("如果未找到匹配，返回: {\"bullKingId\": 0, \"confidence\": 0.0, \"aiAnalysis\": \"未找到合适的匹配\", \"features\": {}}");

            // 使用 DashScope 多模态模型
            String aiResponse = chatModel.call(prompt.toString());

            MatchResultVO result = parseMatchResult(aiResponse, allBulls);

            // 保存匹配图片
            String matchImageUrl = "/match/" + System.currentTimeMillis() + ".jpg";
            try {
                File dir = new File(uploadPath + matchImageUrl).getParentFile();
                if (!dir.exists()) dir.mkdirs();
                imageFile.transferTo(new File(uploadPath + matchImageUrl));
            } catch (Exception e) { log.warn("保存匹配图片失败", e); }

            matchRecordService.saveMatchRecord(
                    result.getBullKingId() != null ? result.getBullKingId() : 0L,
                    matchImageUrl,
                    result.getConfidenceScore().doubleValue());

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("牛王匹配失败: " + e.getMessage());
        }
    }

    @Override
    public FeatureAnalysisVO analyzeFeatures(Long knowledgeFileId) {
        try {
            List<BullKing> allBulls = bullKingMapper.selectList(
                    new LambdaQueryWrapper<BullKing>().orderByDesc(BullKing::getCreateTime).last("LIMIT 50"));

            if (allBulls.isEmpty()) {
                throw new BusinessException("数据库中暂无牛王数据");
            }

            StringBuilder reference = new StringBuilder();
            reference.append("All bull kings in system:\n");
            for (BullKing bk : allBulls) {
                reference.append("- ID=").append(bk.getId())
                        .append(", desc=").append(bk.getDescription() != null ? bk.getDescription() : "none")
                        .append(", record=").append(bk.getBattleRecord() != null ? bk.getBattleRecord() : "none")
                        .append("\n");
            }

            List<String> knowledgeContexts = new ArrayList<>();
            if (knowledgeFileId != null) {
                KnowledgeFile kf = knowledgeFileMapper.selectById(knowledgeFileId);
                if (kf != null && "done".equals(kf.getStatus())) {
                    knowledgeContexts.add("Knowledge file: " + kf.getFilename());
                }
            }

            SearchRequest searchRequest = SearchRequest.builder()
                    .query("bull king features horn head eye fur swirl leg")
                    .topK(10)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(searchRequest);
            for (Document doc : docs) {
                knowledgeContexts.add(doc.getText());
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append(reference.toString());
            if (!knowledgeContexts.isEmpty()) {
                prompt.append("\n\nReference knowledge:\n");
                for (String ctx : knowledgeContexts) {
                    prompt.append("- ").append(ctx.substring(0, Math.min(200, ctx.length()))).append("\n");
                }
            }
            prompt.append("\n\nAnalyze common visual features of these bull kings:\n");
            prompt.append("1. Horn shape and size\n2. Head contour\n3. Eye features\n4. Fur color and texture\n5. Body swirl position and direction\n6. Leg morphology\n");
            prompt.append("\nReturn ONLY a JSON object: {\"hornFeatures\": \"...\", \"headFeatures\": \"...\", \"eyeFeatures\": \"...\", \"furFeatures\": \"...\", \"swirlFeatures\": \"...\", \"legFeatures\": \"...\", \"aiSummary\": \"...\"}");

            String aiResponse = chatModel.call(prompt.toString());

            return parseFeatureAnalysis(aiResponse);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("特征分析失败: " + e.getMessage());
        }
    }

    private MatchResultVO parseMatchResult(String response, List<BullKing> allBulls) {
        MatchResultVO result = new MatchResultVO();
        result.setConfidenceScore(BigDecimal.ZERO);
        String jsonStr = extractJson(response);

        try {
            Pattern bullKingIdPattern = Pattern.compile("\"bullKingId\"\\s*:\\s*(\\d+)");
            Matcher matcher = bullKingIdPattern.matcher(jsonStr);
            if (matcher.find()) {
                Long matchedId = Long.parseLong(matcher.group(1));
                if (matchedId > 0) {
                    BullKing matchedBull = allBulls.stream()
                            .filter(b -> b.getId().equals(matchedId))
                            .findFirst().orElse(null);

                    if (matchedBull != null) {
                        result.setBullKingId(matchedId);
                        result.setDescription(matchedBull.getDescription());
                        result.setBattleRecord(matchedBull.getBattleRecord());

                        List<BullKingImage> images = bullKingImageMapper.selectList(
                                new LambdaQueryWrapper<BullKingImage>().eq(BullKingImage::getBullKingId, matchedId));
                        result.setImages(images.stream()
                                .map(img -> accessUrl + img.getImageUrl())
                                .collect(Collectors.toList()));
                    }
                }
            }

            Pattern confPattern = Pattern.compile("\"confidence\"\\s*:\\s*([\\d.]+)");
            matcher = confPattern.matcher(jsonStr);
            if (matcher.find()) {
                result.setConfidenceScore(new BigDecimal(matcher.group(1)));
            }

            Pattern analysisPattern = Pattern.compile("\"aiAnalysis\"\\s*:\\s*\"([^\"]*)\"");
            matcher = analysisPattern.matcher(jsonStr);
            if (matcher.find()) {
                result.setAiAnalysis(matcher.group(1));
            }

        } catch (Exception e) {
            result.setAiAnalysis("Match analysis: " + response.substring(0, Math.min(500, response.length())));
        }
        return result;
    }

    private FeatureAnalysisVO parseFeatureAnalysis(String response) {
        FeatureAnalysisVO vo = new FeatureAnalysisVO();
        String jsonStr = extractJson(response);

        extractField(jsonStr, "\"hornFeatures\"", vo::setHornFeatures);
        extractField(jsonStr, "\"headFeatures\"", vo::setHeadFeatures);
        extractField(jsonStr, "\"eyeFeatures\"", vo::setEyeFeatures);
        extractField(jsonStr, "\"furFeatures\"", vo::setFurFeatures);
        extractField(jsonStr, "\"swirlFeatures\"", vo::setSwirlFeatures);
        extractField(jsonStr, "\"legFeatures\"", vo::setLegFeatures);
        extractField(jsonStr, "\"aiSummary\"", vo::setAiSummary);

        vo.setAiSummary(vo.getAiSummary() != null ? vo.getAiSummary() : response);
        return vo;
    }

    private void extractField(String json, String key, java.util.function.Consumer<String> setter) {
        try {
            int start = json.indexOf(key);
            if (start >= 0) {
                int colon = json.indexOf(':', start + key.length());
                int quote1 = json.indexOf('"', colon + 1);
                int quote2 = json.indexOf('"', quote1 + 1);
                if (quote2 > quote1) {
                    setter.accept(json.substring(quote1 + 1, quote2));
                }
            }
        } catch (Exception e) { /* regex extraction is best-effort */ }
    }

    private String extractJson(String response) {
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\{(.*?)\\}\\s*```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            return "{" + matcher.group(1) + "}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    // ==================== 人脸识别相关方法 ====================

    @Override
    public float[] extractFaceEmbedding(MultipartFile image) {
        return faceEmbeddingService.extractFaceEmbedding(image);
    }

    @Override
    public FaceMatchResultVO matchFaceByAI(MultipartFile queryImage, List<FaceCandidate> candidates) {
        try {
            byte[] queryBytes = queryImage.getBytes();
            org.springframework.ai.content.Media queryMedia = org.springframework.ai.content.Media.builder()
                    .mimeType(org.springframework.util.MimeTypeUtils.IMAGE_JPEG)
                    .data(queryBytes)
                    .build();

            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个人脸识别专家。你的任务是识别参考照片中哪一张与查询照片匹配。\n\n");
            prompt.append("QUERY PHOTO:\n");
            prompt.append("(This is the photo to be identified)\n\n");

            prompt.append("REFERENCE PHOTOS:\n");
            for (int i = 0; i < candidates.size(); i++) {
                FaceCandidate c = candidates.get(i);
                prompt.append((i + 1)).append(". User ID=").append(c.getUserId())
                        .append(", Name=").append(c.getUserName())
                        .append(", Cosine Similarity=").append(String.format("%.4f", c.getCosineSimilarity()))
                        .append("\n");
            }

            prompt.append("\nCompare the query photo with the reference photos above. ");
            prompt.append("Identify which user is the best match based on facial features. ");
            prompt.append("Return ONLY a JSON object: {\"userId\": <matched_user_id_or_0>, \"confidence\": <0-1>, \"aiAnalysis\": \"brief reasoning\"}\n");
            prompt.append("如果匹配不够好，返回: {\"userId\": 0, \"confidence\": 0.0, \"aiAnalysis\": \"未找到合适的匹配\"}");

            // 构建多模态消息
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

            // 查询图片（带文本和图片）
            UserMessage queryMsg = UserMessage.builder()
                    .text(prompt.toString())
                    .media(queryMedia)
                    .build();
            messages.add(queryMsg);

            // 添加候选人的参考图片
            for (int i = 0; i < candidates.size(); i++) {
                FaceCandidate c = candidates.get(i);
                if (c.getFaceImageUrl() != null && !c.getFaceImageUrl().isEmpty()) {
                    String fullPath = uploadPath + c.getFaceImageUrl();
                    File refFile = new File(fullPath);
                    if (refFile.exists()) {
                        byte[] refBytes = new byte[(int) refFile.length()];
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(refFile)) {
                            fis.read(refBytes);
                        }
                        org.springframework.ai.content.Media refMedia = org.springframework.ai.content.Media.builder()
                                .mimeType(org.springframework.util.MimeTypeUtils.IMAGE_JPEG)
                                .data(refBytes)
                                .build();
                        AssistantMessage candidateMsg = AssistantMessage.builder()
                                .content("Reference photo for user " + c.getUserId() + " (" + c.getUserName() + ")")
                                .media(java.util.List.of(refMedia))
                                .build();
                        messages.add(candidateMsg);
                    }
                }
            }

            // 使用 ChatClient 发送多轮对话
            Prompt multiModalPrompt = new Prompt(messages);
            ChatResponse response = chatModel.call(multiModalPrompt);
            String aiResponse = response.getResults().get(0).getOutput().getText();

            FaceMatchResultVO result = new FaceMatchResultVO();
            result.setCandidates(candidates.stream().map(c -> {
                FaceMatchResultVO.CandidateInfo info = new FaceMatchResultVO.CandidateInfo();
                info.setUserId(c.getUserId());
                info.setUserName(c.getUserName());
                info.setFaceImageUrl(c.getFaceImageUrl());
                info.setCosineSimilarity(new BigDecimal(c.getCosineSimilarity()));
                return info;
            }).collect(Collectors.toList()));

            parseFaceMatchResult(aiResponse, result);

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI人脸精排匹配失败: " + e.getMessage());
        }
    }

    @Override
    public FaceAnalysisResult analyzeFace(MultipartFile image) {
        try {
            String prompt = "Analyze this face photo and evaluate its quality for face recognition. "
                    + "Check: face count, whether it is a clear frontal face, pose angle, lighting quality. "
                    + "Return ONLY a JSON object: {\"faceCount\": <number>, \"isClearFrontal\": <true/false>, "
                    + "\"poseEvaluation\": \"description\", \"lightingEvaluation\": \"description\", "
                    + "\"qualityScore\": <0-1>, \"analysis\": \"summary\"}";

            UserMessage userMsg = UserMessage.builder()
                    .text(prompt)
                    .build();
            Prompt p = new Prompt(userMsg);
            ChatResponse response = chatModel.call(p);
            String aiResponse = response.getResults().get(0).getOutput().getText();

            FaceAnalysisResult result = new FaceAnalysisResult();
            String jsonStr = extractJson(aiResponse);

            try {
                Pattern countPattern = Pattern.compile("\"faceCount\"\\s*:\\s*(\\d+)");
                Matcher m = countPattern.matcher(jsonStr);
                if (m.find()) result.setFaceCount(Integer.parseInt(m.group(1)));

                Pattern boolPattern = Pattern.compile("\"isClearFrontal\"\\s*:\\s*(true|false)");
                m = boolPattern.matcher(jsonStr);
                if (m.find()) result.setIsClearFrontal(Boolean.parseBoolean(m.group(1)));

                extractStringField(jsonStr, "\"poseEvaluation\"", result::setPoseEvaluation);
                extractStringField(jsonStr, "\"lightingEvaluation\"", result::setLightingEvaluation);

                Pattern scorePattern = Pattern.compile("\"qualityScore\"\\s*:\\s*([\\d.]+)");
                m = scorePattern.matcher(jsonStr);
                if (m.find()) result.setQualityScore(Double.parseDouble(m.group(1)));

                extractStringField(jsonStr, "\"analysis\"", result::setAnalysis);
            } catch (Exception ignored) {
                result.setAnalysis(aiResponse);
            }

            if (result.getFaceCount() == null) result.setFaceCount(0);
            if (result.getQualityScore() == null) result.setQualityScore(0.0);

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("人脸分析失败: " + e.getMessage());
        }
    }

    /** 解析逗号分隔的浮点数数组 */
    private float[] parseFloatArray(String text) {
        String cleaned = text.replaceAll("[^0-9.,\\-]", "").trim();
        if (cleaned.isEmpty()) {
            return new float[1024]; // 默认零向量
        }
        String[] parts = cleaned.split(",");
        float[] vector = new float[Math.min(parts.length, 1024)];
        for (int i = 0; i < vector.length; i++) {
            try {
                vector[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                vector[i] = 0f;
            }
        }
        return vector;
    }

    /** 解析人脸匹配结果 */
    private void parseFaceMatchResult(String response, FaceMatchResultVO result) {
        result.setConfidenceScore(BigDecimal.ZERO);
        result.setIsMatch(false);

        String jsonStr = extractJson(response);

        try {
            Pattern userIdPattern = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)");
            Matcher matcher = userIdPattern.matcher(jsonStr);
            if (matcher.find()) {
                Long matchedId = Long.parseLong(matcher.group(1));
                if (matchedId > 0) {
                    result.setUserId(matchedId);
                }
            }

            Pattern confPattern = Pattern.compile("\"confidence\"\\s*:\\s*([\\d.]+)");
            matcher = confPattern.matcher(jsonStr);
            if (matcher.find()) {
                BigDecimal confidence = new BigDecimal(matcher.group(1));
                result.setConfidenceScore(confidence);
                result.setIsMatch(confidence.doubleValue() >= 0.75); // TODO: 阈值应可配置
            }

            Pattern analysisPattern = Pattern.compile("\"aiAnalysis\"\\s*:\\s*\"([^\"]*)\"");
            matcher = analysisPattern.matcher(jsonStr);
            if (matcher.find()) {
                result.setAiAnalysis(matcher.group(1));
            }
        } catch (Exception e) {
            result.setAiAnalysis("AI分析: " + response.substring(0, Math.min(500, response.length())));
        }
    }

    /** 从JSON中抽取字符串字段 */
    private void extractStringField(String json, String key, java.util.function.Consumer<String> setter) {
        try {
            int start = json.indexOf(key);
            if (start >= 0) {
                int colon = json.indexOf(':', start + key.length());
                int quote1 = json.indexOf('"', colon + 1);
                int quote2 = json.indexOf('"', quote1 + 1);
                if (quote2 > quote1) {
                    setter.accept(json.substring(quote1 + 1, quote2));
                }
            }
        } catch (Exception e) { /* regex extraction is best-effort */ }
    }
}
