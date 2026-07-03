package com.niuwang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuwang.common.exception.BusinessException;
import com.niuwang.common.response.PageResult;
import com.niuwang.mapper.KnowledgeFileMapper;
import com.niuwang.model.entity.KnowledgeFile;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现类
 * 支持 PDF/Word/Excel 文件上传，使用 AI 提取内容并存入向量库
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphServiceImpl extends ServiceImpl<KnowledgeFileMapper, KnowledgeFile> implements KnowledgeGraphService {

    private final VectorStore vectorStore;

    @Value("${file.upload.path}")
    private String uploadPath;

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
        kf.setStatus("pending");
        save(kf);

        // 将文件内容读取为字节数组，避免异步线程中临时文件被清理
        try {
            byte[] fileBytes = file.getBytes();
            processKnowledgeFileAsync(kf.getId(), fileBytes, ext);
        } catch (IOException e) {
            kf.setStatus("error");
            kf.setErrorMsg("读取文件失败: " + e.getMessage());
            updateById(kf);
        }
    }

    @Async
    public void processKnowledgeFileAsync(Long fileId, byte[] fileBytes, String ext) {
        KnowledgeFile kf = getById(fileId);
        if (kf == null) return;

        try {
            kf.setStatus("processing");
            updateById(kf);

            // 保存文件到本地
            String fileName = System.currentTimeMillis() + "." + ext;
            String filePath = "/knowledge/" + fileName;
            java.io.File fileDir = new java.io.File(uploadPath + filePath).getParentFile();
            if (!fileDir.exists()) fileDir.mkdirs();
            java.nio.file.Files.write(new java.io.File(uploadPath + filePath).toPath(), fileBytes);
            kf.setFilePath(filePath);

            // 从字节数组提取文本
            List<Document> documents = extractTextFromFile(fileBytes);

            // 存入向量库
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
            }

            kf.setStatus("done");
            updateById(kf);

        } catch (Exception e) {
            kf.setStatus("error");
            kf.setErrorMsg(e.getMessage());
            updateById(kf);
        }
    }

    /**
     * 使用 Apache Tika 从各种文件格式中提取文本
     */
    private List<Document> extractTextFromFile(byte[] fileBytes) throws Exception {
        org.apache.tika.Tika tika = new org.apache.tika.Tika();
        String text = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
        Document doc = new Document(text);
        return List.of(doc);
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
            vo.setStatus(kf.getStatus());
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
    }
}
