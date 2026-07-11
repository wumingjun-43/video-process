package com.niuwang.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuwang.common.response.PageResult;
import com.niuwang.mapper.MatchRecordMapper;
import com.niuwang.model.entity.MatchRecord;
import com.niuwang.service.MatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 匹配记录服务实现类
 */
@Service
@RequiredArgsConstructor
public class MatchRecordServiceImpl extends ServiceImpl<MatchRecordMapper, MatchRecord> implements MatchRecordService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveMatchRecord(Long bullKingId, String imageUrl, Double confidenceScore) {
        MatchRecord record = new MatchRecord();
        record.setBullKingId(bullKingId);
        record.setImageUrl(imageUrl);
        record.setConfidenceScore(BigDecimal.valueOf(confidenceScore));
        save(record);
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveFaceMatchRecord(Long userId, String imageUrl, Double confidenceScore) {
        MatchRecord record = new MatchRecord();
        record.setUserId(userId);
        record.setImageUrl(imageUrl);
        record.setConfidenceScore(BigDecimal.valueOf(confidenceScore));
        save(record);
        return record.getId();
    }

    @Override
    public PageResult<MatchRecord> pageMatchRecord(long page, long size) {
        Page<MatchRecord> pageParam = new Page<>(page, size);
        Page<MatchRecord> result = page(pageParam, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchRecord>()
                .orderByDesc(MatchRecord::getCreateTime));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }
}
