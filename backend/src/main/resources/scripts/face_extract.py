#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
人脸特征提取脚本
使用 insightface (buffalo_l) 模型提取 512 维人脸特征向量
输入: JSON from stdin {"image": "base64", "action": "extract|compare"}
输出: JSON to stdout {"embedding": [...], "bbox": [...]} 或 {"similarity": 0.xx}
"""

import sys
import json
import base64

# 延迟导入，避免在非人脸场景报错
try:
    import cv2
    import numpy as np
    from insightface.app import FaceAnalysis
    INSIGHTFACE_READY = True
except ImportError:
    INSIGHTFACE_READY = False


def extract_embedding(image_b64):
    """从 base64 图片提取 512 维人脸特征向量"""
    if not INSIGHTFACE_READY:
        return {"error": "insightface not installed"}

    # 初始化（单次进程只初始化一次）
    if not hasattr(extract_embedding, 'app'):
        extract_embedding.app = FaceAnalysis(
            name='buffalo_l',
            providers=['CPUExecutionProvider']
        )
        extract_embedding.app.prepare(ctx_id=0)

    # base64 → numpy array
    img_bytes = base64.b64decode(image_b64)
    img_np = cv2.imdecode(np.frombuffer(img_bytes, np.uint8), cv2.IMREAD_COLOR)

    if img_np is None:
        return {"error": "Failed to decode image"}

    # 检测人脸并提取特征
    faces = extract_embedding.app.get(img_np)

    if len(faces) == 0:
        return {"error": "No face detected", "embedding": None}

    # 取最大脸（面积最大的）
    largest = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
    embedding = largest.embedding.tolist()
    bbox = largest.bbox.tolist()

    return {
        "embedding": embedding,
        "bbox": bbox,
        "face_count": len(faces)
    }


def compute_similarity(emb1, emb2):
    """计算两个 embedding 的余弦相似度"""
    v1 = np.array(emb1)
    v2 = np.array(emb2)
    norm1 = np.linalg.norm(v1)
    norm2 = np.linalg.norm(v2)
    if norm1 == 0 or norm2 == 0:
        return 0.0
    return float(np.dot(v1, v2) / (norm1 * norm2))


def compare_faces(image1_b64, image2_b64):
    """比较两张人脸图片的相似度"""
    if not INSIGHTFACE_READY:
        return {"error": "insightface not installed"}

    result1 = extract_embedding(image1_b64)
    result2 = extract_embedding(image2_b64)

    if "error" in result1 or "error" in result2:
        return {"error": "Face detection failed"}

    similarity = compute_similarity(result1["embedding"], result2["embedding"])
    return {"similarity": similarity}


def main():
    """从 stdin 读取 JSON，处理后输出到 stdout"""
    try:
        data = json.loads(sys.stdin.read())
    except json.JSONDecodeError:
        print(json.dumps({"error": "Invalid JSON input"}))
        return

    action = data.get("action", "extract")
    image = data.get("image", "")

    if action == "extract":
        result = extract_embedding(image)
        print(json.dumps(result))

    elif action == "compare":
        image2 = data.get("image2", "")
        result = compare_faces(image, image2)
        print(json.dumps(result))

    else:
        print(json.dumps({"error": f"Unknown action: {action}"}))


if __name__ == "__main__":
    main()
