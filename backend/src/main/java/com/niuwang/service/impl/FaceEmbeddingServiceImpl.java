package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.service.FaceEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FaceEmbeddingServiceImpl implements FaceEmbeddingService {

    @Value("${face.python-script:classpath:scripts/face_extract.py}")
    private String pythonScriptPath;

    @Value("${face.python-command:python}")
    private String pythonCommand;

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
        ProcessBuilder pb = new ProcessBuilder(pythonCommand, resolveScriptPath());
        // Don't redirect error stream — keep stdout clean for JSON
        Process process = pb.start();

        String jsonInput = String.format("{\"image\":\"%s\",\"action\":\"extract\"}", base64Image);
        process.getOutputStream().write(jsonInput.getBytes("UTF-8"));
        process.getOutputStream().flush();
        process.getOutputStream().close();

        String output = readStdout(process);
        // Drain stderr silently
        try (java.io.BufferedReader errReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream(), "UTF-8"))) {
            while (errReader.readLine() != null) {}
        }

        return parseEmbedding(output);
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

    private float[] parseEmbedding(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = mapper.readValue(json, java.util.Map.class);

            if (data.containsKey("error")) {
                throw new BusinessException((String) data.get("error"));
            }

            @SuppressWarnings("unchecked")
            java.util.List<Double> embList = (java.util.List<Double>) data.get("embedding");
            if (embList == null) {
                throw new BusinessException("未能提取到人脸特征向量");
            }

            float[] embedding = new float[embList.size()];
            for (int i = 0; i < embList.size(); i++) {
                embedding[i] = embList.get(i).floatValue();
            }
            return embedding;
        } catch (Exception e) {
            throw new BusinessException("解析 Python 输出失败: " + e.getMessage());
        }
    }

    private double parseSimilarity(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
