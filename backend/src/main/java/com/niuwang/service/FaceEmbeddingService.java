package com.niuwang.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 人脸特征提取服务
 * 桥接 Java 和 Python insightface 库
 */
public interface FaceEmbeddingService {

    float[] extractFaceEmbedding(MultipartFile image);

    float[] extractFaceEmbedding(File imageFile);

    float[] extractFaceEmbedding(byte[] imageBytes);

    double compareFaces(MultipartFile image1, MultipartFile image2);

    String toPgVectorString(float[] embedding);
}
