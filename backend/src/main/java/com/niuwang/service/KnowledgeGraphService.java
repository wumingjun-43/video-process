package com.niuwang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuwang.common.response.PageResult;
import com.niuwang.model.entity.KnowledgeFile;
import com.niuwang.model.vo.KnowledgeFileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识图谱服务接口
 */
public interface KnowledgeGraphService extends IService<KnowledgeFile> {

    /** 上传知识图谱文件 */
    void uploadKnowledgeFile(MultipartFile file);

    /** 分页查询知识文件列表 */
    PageResult<KnowledgeFileVO> pageKnowledge(long page, long size);

    /** 删除知识文件 */
    void deleteKnowledgeFile(Long id);
}
