package com.niuwang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niuwang.common.response.PageResult;
import com.niuwang.common.response.Result;
import com.niuwang.model.dto.BullKingDTO;
import com.niuwang.model.dto.BullKingMatchDTO;
import com.niuwang.model.vo.BullKingVO;
import com.niuwang.model.vo.FeatureAnalysisVO;
import com.niuwang.model.vo.MatchResultVO;
import com.niuwang.service.AiRecognitionService;
import com.niuwang.service.BullKingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 牛王控制器
 */
@RestController
@RequestMapping("/bull-king")
@RequiredArgsConstructor
public class BullKingController {

    private final BullKingService bullKingService;
    private final AiRecognitionService aiRecognitionService;

    @PostMapping
    public Result<Long> addBullKing(@ModelAttribute @Valid BullKingDTO dto) {
        return Result.success(bullKingService.addBullKing(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> updateBullKing(@PathVariable Long id,
                                        @ModelAttribute BullKingDTO dto,
                                        @RequestParam(required = false) String retainedUrls) {
        // 解析 retainedUrls JSON 字符串为 List
        if (retainedUrls != null && !retainedUrls.isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                dto.setRetainedUrls(mapper.readValue(retainedUrls, List.class));
            } catch (Exception e) {
                // ignore parse error
            }
        }
        bullKingService.updateBullKing(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<BullKingVO> getBullKingDetail(@PathVariable Long id) {
        return Result.success(bullKingService.getBullKingDetail(id));
    }

    @GetMapping
    public Result<PageResult<BullKingVO>> pageBullKing(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(bullKingService.pageBullKing(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteBullKing(@PathVariable Long id) {
        bullKingService.deleteBullKing(id);
        return Result.success();
    }

    @PostMapping("/match")
    public Result<MatchResultVO> matchBullKing(@ModelAttribute @Valid BullKingMatchDTO dto) {
        return Result.success(aiRecognitionService.matchBullKing(dto));
    }

    @PostMapping("/features")
    public Result<FeatureAnalysisVO> analyzeFeatures(
            @RequestParam(required = false) Long knowledgeFileId) {
        return Result.success(aiRecognitionService.analyzeFeatures(knowledgeFileId));
    }
}
