package com.niuwang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuwang.common.response.PageResult;
import com.niuwang.model.entity.MatchRecord;

/**
 * 匹配记录服务接口
 */
public interface MatchRecordService extends IService<MatchRecord> {

    /** 保存匹配记录 */
    Long saveMatchRecord(Long bullKingId, String imageUrl, Double confidenceScore);

    /** 保存人脸识别匹配记录 */
    Long saveFaceMatchRecord(Long userId, String imageUrl, Double confidenceScore);

    /** 分页查询匹配记录 */
    PageResult<MatchRecord> pageMatchRecord(long page, long size);
}
