package com.niuwang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuwang.common.exception.BusinessException;
import com.niuwang.common.response.PageResult;
import com.niuwang.mapper.KnowledgeFileMapper;
import com.niuwang.model.entity.KnowledgeFile;
import com.niuwang.model.enums.KnowledgeFileStatus;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.DocumentChunkerService;
import com.niuwang.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现类
 * 离线阶段：文档加载(Tika) → 切分(Chunking) → 向量化(Embedding) → 入库(pgvector)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphServiceImpl extends ServiceImpl<KnowledgeFileMapper, KnowledgeFile> implements KnowledgeGraphService {

    private final VectorStore vectorStore;
    private final DocumentChunkerService documentChunkerService;
    private final TransactionTemplate transactionTemplate;
    private final org.springframework.beans.factory.ObjectProvider<KnowledgeGraphServiceImpl> selfProvider;

    /**
     * 懒加载的自身代理，用于调用 @Async 方法。
     * 使用 ObjectProvider 而非直接注入，避免循环依赖。
     */
    private KnowledgeGraphServiceImpl self() {
        return selfProvider.getObject();
    }

    @Value("${file.upload.path}")
    private String uploadPath;

    /** 每个 chunk 最大 token 数 */
    private static final int CHUNK_MAX_TOKENS = 600;

    /** 相邻 chunk 重叠 token 数 */
    private static final int CHUNK_OVERLAP_TOKENS = 100;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadKnowledgeFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }

        // 只允许 pdf, doc, docx, xls, xlsx
        List<String> allowed = List.of("pdf", "doc", "docx", "xls", "xlsx");
        if (!allowed.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅支持 PDF/Word/Excel");
        }

        KnowledgeFile kf = new KnowledgeFile();
        kf.setFilename(originalName != null ? originalName : "unknown");
        kf.setFileType(ext);
        kf.setStatus(KnowledgeFileStatus.pending);
        kf.setFilePath("");
        save(kf);

        // 将文件内容读取为字节数组，避免异步线程中临时文件被清理
        try {
            byte[] fileBytes = file.getBytes();
            self().processKnowledgeFileAsync(kf.getId(), fileBytes, ext);
        } catch (IOException e) {
            kf.setStatus(KnowledgeFileStatus.error);
            kf.setErrorMsg("读取文件失败: " + e.getMessage());
            updateById(kf);
        }
    }

    /**
     * 离线阶段：异步处理知识文件
     * 流程：Tika 解析 → 文档切分 → Embedding 向量化 → pgvector 入库
     *
     * 注意：@Async 方法无法通过 @Transactional 获得事务（事务在原始线程获取），
     * 因此使用 TransactionTemplate 显式管理事务。
     */
    @Async
    public void processKnowledgeFileAsync(Long fileId, byte[] fileBytes, String ext) {
        KnowledgeFile kf = getById(fileId);
        if (kf == null) return;

        try {
            // 在独立事务中执行所有 DB 写操作，确保原子性
            transactionTemplate.execute(status -> {
                try {
                    kf.setStatus(KnowledgeFileStatus.processing);
                    baseMapper.updateById(kf);

                    // 1. 保存文件到本地
                    String fileName = System.currentTimeMillis() + "." + ext;
                    String filePath = "/knowledge/" + fileName;
                    java.io.File fileDir = new java.io.File(uploadPath + filePath).getParentFile();
                    if (!fileDir.exists()) fileDir.mkdirs();
                    java.nio.file.Files.write(new java.io.File(uploadPath + filePath).toPath(), fileBytes);
                    kf.setFilePath(filePath);

                    // 2. 使用 TikaDocumentReader 解析文档（支持 PDF/Word/Excel 等格式）
                    List<Document> rawDocuments = extractDocuments(fileBytes, kf.getId());

                    if (rawDocuments.isEmpty()) {
                        kf.setStatus(KnowledgeFileStatus.error);
                        kf.setErrorMsg("文件解析失败，未提取到任何内容");
                        baseMapper.updateById(kf);
                        return null;
                    }

                    log.info("文件 {} 通过 Tika 解析得到 {} 个原始文档块", kf.getFilename(), rawDocuments.size());

                    // 3. 文档切分（Chunking）
                    List<Document> documents = new java.util.ArrayList<>();
                    for (Document rawDoc : rawDocuments) {
                        documents.addAll(documentChunkerService.chunk(
                                rawDoc.getText(), kf.getId(), CHUNK_MAX_TOKENS, CHUNK_OVERLAP_TOKENS));
                    }

                    if (documents.isEmpty()) {
                        kf.setStatus(KnowledgeFileStatus.error);
                        kf.setErrorMsg("文档切分失败");
                        baseMapper.updateById(kf);
                        return null;
                    }

                    log.info("文件 {} 切分为 {} 个 chunk，准备向量化入库", kf.getFilename(), documents.size());

                    // 4. 存入向量库（自动调用 Embedding 模型向量化 → pgvector）
                    vectorStore.add(documents);

                    kf.setStatus(KnowledgeFileStatus.done);
                    baseMapper.updateById(kf);
                    log.info("知识文件处理完成: {}, {} 个 chunk 已入库", kf.getFilename(), documents.size());
                    return null;
                } catch (Exception e) {
                    // 标记回滚，让 TransactionTemplate 回滚整个事务
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            // TransactionTemplate 回滚后，确保文件状态设为 error
            log.error("知识文件处理失败: fileId={}", fileId, e);
            KnowledgeFile updateKf = getById(fileId);
            if (updateKf != null && updateKf.getStatus() != KnowledgeFileStatus.done) {
                updateKf.setStatus(KnowledgeFileStatus.error);
                updateKf.setErrorMsg("处理失败: " + e.getCause().getMessage());
                updateById(updateKf);
            }
        }
    }

    /**
     * 使用 TikaDocumentReader 解析文档为 Document 列表
     * 支持 PDF、Word、Excel 等多种格式
     */
    private List<Document> extractDocuments(byte[] fileBytes, Long sourceId) {
        try {
            log.info("开始 Tika 文档解析: 文件大小={} bytes", fileBytes.length);

            // 构造 Resource
            InputStreamResource resource = new InputStreamResource(
                    new java.io.ByteArrayInputStream(fileBytes));

            // 通过 TikaDocumentReader 解析
            TikaDocumentReader reader = new TikaDocumentReader(resource);

            // 解析后给每个文档添加 source_id metadata
            List<Document> documents = reader.get();
            log.info("Tika 解析完成: 提取 {} 个文档块", documents.size());

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                Map<String, Object> metadata = doc.getMetadata();
                metadata.put("source_id", sourceId);
            }

            if (documents.isEmpty()) {
                log.warn("Tika 解析文件后未提取到文档内容");
            }
            return documents;

        } catch (Exception e) {
            log.error("Tika 文档解析失败: 文件大小={} bytes, 错误={}", fileBytes.length, e.getMessage(), e);
            throw new BusinessException("文档解析失败: " + e.getMessage());
        }
    }

    @Override
    public PageResult<KnowledgeFileVO> pageKnowledge(long page, long size) {
        Page<KnowledgeFile> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KnowledgeFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(KnowledgeFile::getCreateTime);
        Page<KnowledgeFile> result = page(pageParam, wrapper);

        List<KnowledgeFileVO> voList = result.getRecords().stream().map(kf -> {
            KnowledgeFileVO vo = new KnowledgeFileVO();
            vo.setId(kf.getId());
            vo.setFilename(kf.getFilename());
            vo.setFileType(kf.getFileType());
            vo.setStatus(kf.getStatus().getCode());
            vo.setErrorMsg(kf.getErrorMsg());
            vo.setCreateTime(kf.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeFile(Long id) {
        removeById(id);
        log.info("知识文件已删除: id={}", id);
    }
}
