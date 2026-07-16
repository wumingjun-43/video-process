package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.service.FaceEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 人脸特征提取服务实现
 * 流程: insightface (512维) → DashScope embedding (1024维)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceEmbeddingServiceImpl implements FaceEmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Value("${face.python-script:classpath:scripts/face_extract.py}")
    private String pythonScriptPath;

    @Value("${face.python-command:python}")
    private String pythonCommand;

    /** insightface buffalo_l 输出维度 */
    private static final int INSIGHTFACE_DIM = 512;

    /** DashScope embedding 输出维度 */
    private static final int TARGET_DIM = 1024;

    @Override
    public float[] extractFaceEmbedding(MultipartFile image) {
        try {
            byte[] bytes = image.getBytes();
            return extractFromBase64(Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    @Override
    public float[] extractFaceEmbedding(File imageFile) {
        try {
            byte[] bytes = readFileToBytes(imageFile);
            return extractFromBase64(Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    @Override
    public float[] extractFaceEmbedding(byte[] imageBytes) {
        try {
            return extractFromBase64(Base64.getEncoder().encodeToString(imageBytes));
        } catch (Exception e) {
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    @Override
    public double compareFaces(MultipartFile image1, MultipartFile image2) {
        try {
            String b64_1 = Base64.getEncoder().encodeToString(image1.getBytes());
            String b64_2 = Base64.getEncoder().encodeToString(image2.getBytes());
            return extractFromBase64Compare(b64_1, b64_2);
        } catch (Exception e) {
            throw new BusinessException("人脸相似度比较失败: " + e.getMessage());
        }
    }

    @Override
    public String toPgVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private float[] extractFromBase64(String base64Image) throws Exception {
        // Step 1: 通过 Python insightface 提取 512 维向量
        float[] insightfaceEmbedding = extractInsightfaceEmbedding(base64Image);

        // Step 2: 通过 DashScope embedding 模型升维到 1024 维
        return embedToTargetDimension(insightfaceEmbedding);
    }

    private double extractFromBase64Compare(String b64_1, String b64_2) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(pythonCommand, resolveScriptPath());
        Process process = pb.start();

        String jsonInput = String.format(
                "{\"image\":\"%s\",\"image2\":\"%s\",\"action\":\"compare\"}", b64_1, b64_2);
        process.getOutputStream().write(jsonInput.getBytes("UTF-8"));
        process.getOutputStream().flush();
        process.getOutputStream().close();

        String output = readStdout(process);
        try (java.io.BufferedReader errReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream(), "UTF-8"))) {
            while (errReader.readLine() != null) {}
        }

        return parseSimilarity(output);
    }

    /**
     * 调用 Python insightface 提取 512 维人脸特征向量
     */
    private float[] extractInsightfaceEmbedding(String base64Image) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(pythonCommand, resolveScriptPath());
        Process process = pb.start();

        String jsonInput = String.format("{\"image\":\"%s\",\"action\":\"extract\"}", base64Image);
        process.getOutputStream().write(jsonInput.getBytes("UTF-8"));
        process.getOutputStream().flush();
        process.getOutputStream().close();

        String output = readStdout(process);
        return parseEmbedding(output);
    }

    /**
     * 将 insightface 512 维向量通过 DashScope embedding 模型升维到 1024 维
     */
    private float[] embedToTargetDimension(float[] insightfaceVector) {
        try {
            // 将 512 维向量转回文本描述，交给 DashScope embedding 模型编码
            // 使用向量均值作为文本表征，DashScope 会输出 1024 维
            String text = formatVectorForEmbedding(insightfaceVector);

            float[] embedding = embeddingModel.embed(text);

            // 确保维度正确
            if (embedding.length != TARGET_DIM) {
                log.warn("DashScope 输出维度 {} 不等于期望的 {}, 自动补齐/截断", embedding.length, TARGET_DIM);
                float[] adjusted = new float[TARGET_DIM];
                int len = Math.min(embedding.length, TARGET_DIM);
                System.arraycopy(embedding, 0, adjusted, 0, len);
                return adjusted;
            }
            return embedding;

        } catch (Exception e) {
            log.error("DashScope 升维失败，回退使用 insightface 原始向量", e);
            // 回退：补齐到 1024 维
            float[] fallback = new float[TARGET_DIM];
            int len = Math.min(insightfaceVector.length, TARGET_DIM);
            System.arraycopy(insightfaceVector, 0, fallback, 0, len);
            return fallback;
        }
    }

    /**
     * 将向量编码为文本描述，供 DashScope embedding 使用
     */
    private String formatVectorForEmbedding(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append("Face feature vector: ");
        int dim = Math.min(vector.length, 64); // 取前64维作为特征描述
        for (int i = 0; i < dim; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", vector[i]));
        }
        return sb.toString();
    }

    private float[] parseEmbedding(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = mapper.readValue(json, java.util.Map.class);

            if (data.containsKey("error")) {
                throw new BusinessException((String) data.get("error"));
            }

            @SuppressWarnings("unchecked")
            java.util.List<Double> embList = (java.util.List<Double>) data.get("embedding");
            if (embList == null) {
                throw new BusinessException("未能提取到人脸特征向量");
            }

            // insightface buffalo_l 输出 512 维
            if (embList.size() != INSIGHTFACE_DIM) {
                log.warn("insightface 输出维度 {} 不是预期的 {}", embList.size(), INSIGHTFACE_DIM);
            }

            float[] embedding = new float[INSIGHTFACE_DIM];
            for (int i = 0; i < INSIGHTFACE_DIM; i++) {
                embedding[i] = embList.get(i).floatValue();
            }
            return embedding;
        } catch (Exception e) {
            throw new BusinessException("解析 Python 输出失败: " + e.getMessage());
        }
    }

    private double parseSimilarity(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = mapper.readValue(json, java.util.Map.class);

            if (data.containsKey("error")) {
                throw new BusinessException((String) data.get("error"));
            }

            return (double) data.get("similarity");
        } catch (Exception e) {
            throw new BusinessException("解析 Python 输出失败: " + e.getMessage());
        }
    }

    private String readStdout(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new BusinessException("Python 脚本执行超时 (30s)");
        }

        if (process.exitValue() != 0) {
            throw new BusinessException("Python 脚本执行失败，退出码: " + process.exitValue());
        }

        return sb.toString().trim();
    }

    private String resolveScriptPath() {
        if (pythonScriptPath.startsWith("classpath:")) {
            String relativePath = pythonScriptPath.substring("classpath:".length());
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath);
                if (is == null) {
                    throw new BusinessException("找不到 Python 脚本: " + relativePath);
                }
                File tempScript = File.createTempFile("face_extract", ".py");
                tempScript.deleteOnExit();
                java.nio.file.Files.copy(is, tempScript.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return tempScript.getAbsolutePath();
            } catch (Exception e) {
                throw new BusinessException("加载 Python 脚本失败: " + e.getMessage());
            }
        }
        return pythonScriptPath;
    }

    private byte[] readFileToBytes(File file) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = fis.read(buffer)) > 0) {
                bos.write(buffer, 0, n);
            }
        }
        return bos.toByteArray();
    }
}
