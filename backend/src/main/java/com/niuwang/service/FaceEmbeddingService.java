package com.niuwang.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸特征提取服务
 * 桥接 Java 和 Python insightface 库
 */
public interface FaceEmbeddingService {

    /**
     * 从人脸图片提取 512 维特征向量
     *
     * @param image 人脸照片
     * @return 512 维 float 数组
     * @throws RuntimeException 当没有检测到人脸或提取失败时抛出
     */
    float[] extractFaceEmbedding(MultipartFile image);

    /**
     * 计算两张人脸图片的余弦相似度
     *
     * @param image1 第一张人脸照片
     * @param image2 第二张人脸照片
     * @return 余弦相似度 (0-1)
     */
    double compareFaces(MultipartFile image1, MultipartFile image2);

    /**
     * 将 float[] 转为 pgvector 格式字符串 "[0.1,0.2,...]"
     */
    String toPgVectorString(float[] embedding);
}