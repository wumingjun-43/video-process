# =====================================================
# RAG 智能对话 - 离线阶段 + 在线阶段 完整实现
# =====================================================
#
# 离线阶段（知识图谱上传时执行）：
#   1. 文档加载 → Apache Tika 解析 PDF/Word/Excel
#   2. 文档切割 (Chunking) → 按段落/句子分割，500 token/chunk，重叠 100 token
#   3. Embedding 向量化 → DashScope embedding 模型
#   4. 入库 → PostgreSQL pgvector 向量库
#
# 在线阶段（用户提问时执行）：
#   1. Query 处理 → 意图分析 + 语义重写
#   2. 向量检索 (粗排) → pgvector 相似度搜索 Top-20
#   3. Rerank (精排) → 语义相关性重排序，取 Top-5
#   4. 生成答案 → 组装 Prompt + DashScope 大模型
