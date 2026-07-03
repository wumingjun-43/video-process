package com.niuwang.controller;

import com.niuwang.common.response.PageResult;
import com.niuwang.common.response.Result;
import com.niuwang.model.entity.MatchRecord;
import com.niuwang.service.MatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 匹配记录控制器
 */
@RestController
@RequestMapping("/match-record")
@RequiredArgsConstructor
public class MatchRecordController {

    private final MatchRecordService matchRecordService;

    @GetMapping
    public Result<PageResult<MatchRecord>> pageMatchRecord(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(matchRecordService.pageMatchRecord(page, size));
    }

    @GetMapping("/{id}")
    public Result<MatchRecord> getMatchRecord(@PathVariable Long id) {
        return Result.success(matchRecordService.getById(id));
    }
}
