package com.niuwang.controller;

import com.niuwang.common.response.PageResult;
import com.niuwang.common.response.Result;
import com.niuwang.model.vo.KnowledgeFileVO;
import com.niuwang.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识图谱控制器
 */
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    @PostMapping("/upload")
    public Result<Void> uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
        knowledgeGraphService.uploadKnowledgeFile(file);
        return Result.success("上传成功，文件正在处理中");
    }

    @GetMapping
    public Result<PageResult<KnowledgeFileVO>> pageKnowledge(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(knowledgeGraphService.pageKnowledge(page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeFile(@PathVariable Long id) {
        knowledgeGraphService.deleteKnowledgeFile(id);
        return Result.success();
    }
}
