package com.niuwang.service.impl;

import com.niuwang.common.exception.BusinessException;
import com.niuwang.service.FaceEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 人脸特征提取服务实现
 * 通过 subprocess 调用 Python insightface 脚本提取 512 维人脸特征向量
 */
@Slf4j
@Service
public class FaceEmbeddingServiceImpl implements FaceEmbeddingService {

    @Value("${face.python-script:classpath:scripts/face_extract.py}")
    private String pythonScriptPath;

    @Value("${face.python-command:python}")
    private String pythonCommand;

    /**
     * 从人脸图片提取 512 维特征向量
     */
    @Override
    public float[] extractFaceEmbedding(MultipartFile image) {
        try {
            // 1. 图片转 base64
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 2. 构建 Python 命令
            String scriptPath = resolveScriptPath();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCommand, scriptPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 3. 写入 JSON 输入
            String jsonInput = String.format(
                    "{\"image\":\"%s\",\"action\":\"extract\"}",
                    base64Image
            );
            process.getOutputStream().write(jsonInput.getBytes("UTF-8"));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            // 4. 读取输出
            String output = readOutput(process);

            // 5. 解析 JSON 结果
            Map<String, Object> result = parseJson(output);

            if (result.containsKey("error")) {
                throw new BusinessException((String) result.get("error"));
            }

            @SuppressWarnings("unchecked")
            java.util.List<Double> embList = (java.util.List<Double>) result.get("embedding");
            if (embList == null) {
                throw new BusinessException("未能提取到人脸特征向量");
            }

            float[] embedding = new float[embList.size()];
            for (int i = 0; i < embList.size(); i++) {
                embedding[i] = embList.get(i).floatValue();
            }

            log.debug("人脸特征提取完成，维度: {}", embedding.length);
            return embedding;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("人脸特征提取失败: {}", e.getMessage(), e);
            throw new BusinessException("人脸特征提取失败: " + e.getMessage());
        }
    }

    /**
     * 比较两张人脸图片的余弦相似度
     */
    @Override
    public double compareFaces(MultipartFile image1, MultipartFile image2) {
        try {
            String base64Image1 = Base64.getEncoder().encodeToString(image1.getBytes());
            String base64Image2 = Base64.getEncoder().encodeToString(image2.getBytes());

            String scriptPath = resolveScriptPath();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCommand, scriptPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String jsonInput = String.format(
                    "{\"image\":\"%s\",\"image2\":\"%s\",\"action\":\"compare\"}",
                    base64Image1, base64Image2
            );
            process.getOutputStream().write(jsonInput.getBytes("UTF-8"));
            process.getOutputStream().flush();
            process.getOutputStream().close();

            String output = readOutput(process);
            Map<String, Object> result = parseJson(output);

            if (result.containsKey("error")) {
                throw new BusinessException((String) result.get("error"));
            }

            return (double) result.get("similarity");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("人脸相似度比较失败: {}", e.getMessage(), e);
            throw new BusinessException("人脸相似度比较失败: " + e.getMessage());
        }
    }

    /**
     * 将 float[] 转为 pgvector 格式字符串
     */
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

    /**
     * 解析 Python 输出的 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new BusinessException("解析 Python 输出失败: " + e.getMessage());
        }
    }

    /**
     * 读取 subprocess 输出
     */
    private String readOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        // 等待进程完成（超时 30 秒）
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

    /**
     * 解析 Python 脚本路径
     */
    private String resolveScriptPath() {
        // classpath: 前缀表示从 resources 中读取
        if (pythonScriptPath.startsWith("classpath:")) {
            String relativePath = pythonScriptPath.substring("classpath:".length());
            // 从 classpath 加载到临时文件
            try {
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath);
                if (is == null) {
                    throw new BusinessException("找不到 Python 脚本: " + relativePath);
                }
                java.io.File tempScript = java.io.File.createTempFile("face_extract", ".py");
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
}
