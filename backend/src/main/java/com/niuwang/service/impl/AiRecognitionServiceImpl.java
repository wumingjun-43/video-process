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
import com.niuwang.model.vo.FeatureAnalysisVO;
import com.niuwang.model.vo.MatchResultVO;
import com.niuwang.service.AiRecognitionService;
import com.niuwang.service.MatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 识别服务实现类
 * 基于 spring-ai-alibaba DashScope 模型进行牛王匹配和特征分析
 */
@Service
@RequiredArgsConstructor
public class AiRecognitionServiceImpl implements AiRecognitionService {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final BullKingMapper bullKingMapper;
    private final BullKingImageMapper bullKingImageMapper;
    private final KnowledgeFileMapper knowledgeFileMapper;
    private final MatchRecordService matchRecordService;

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
            prompt.append("You are a bull king image recognition assistant. Analyze this image and match it with known bull kings.\n\n");
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
            prompt.append("If no match found, return {\"bullKingId\": 0, \"confidence\": 0.0, \"aiAnalysis\": \"No match found\", \"features\": {}}");

            // 使用 DashScope 多模态模型
            String aiResponse = chatModel.call(prompt.toString());

            MatchResultVO result = parseMatchResult(aiResponse, allBulls);

            // 保存匹配图片
            String matchImageUrl = "/match/" + System.currentTimeMillis() + ".jpg";
            try {
                File dir = new File(uploadPath + matchImageUrl).getParentFile();
                if (!dir.exists()) dir.mkdirs();
                imageFile.transferTo(new File(uploadPath + matchImageUrl));
            } catch (Exception e) { /* ignore */ }

            matchRecordService.saveMatchRecord(
                    result.getBullKingId() != null ? result.getBullKingId() : 0L,
                    matchImageUrl,
                    result.getConfidenceScore().doubleValue());

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI matching failed: " + e.getMessage());
        }
    }

    @Override
    public FeatureAnalysisVO analyzeFeatures(Long knowledgeFileId) {
        try {
            List<BullKing> allBulls = bullKingMapper.selectList(
                    new LambdaQueryWrapper<BullKing>().orderByDesc(BullKing::getCreateTime));

            if (allBulls.isEmpty()) {
                throw new BusinessException("No bull king data available for analysis");
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
            throw new BusinessException("AI feature analysis failed: " + e.getMessage());
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
        } catch (Exception ignored) {}
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
}
