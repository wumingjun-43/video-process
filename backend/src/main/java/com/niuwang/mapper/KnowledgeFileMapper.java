package com.niuwang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuwang.model.entity.KnowledgeFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱文件 Mapper 接口
 */
@Mapper
public interface KnowledgeFileMapper extends BaseMapper<KnowledgeFile> {
}
