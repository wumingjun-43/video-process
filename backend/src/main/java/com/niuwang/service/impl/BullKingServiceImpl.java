package com.niuwang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuwang.common.exception.BusinessException;
import com.niuwang.common.response.PageResult;
import com.niuwang.mapper.BullKingImageMapper;
import com.niuwang.mapper.BullKingMapper;
import com.niuwang.model.dto.BullKingDTO;
import com.niuwang.model.entity.BullKing;
import com.niuwang.model.entity.BullKingImage;
import com.niuwang.model.vo.BullKingVO;
import com.niuwang.service.BullKingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 牛王服务实现类
 */
@Service
@RequiredArgsConstructor
public class BullKingServiceImpl extends ServiceImpl<BullKingMapper, BullKing> implements BullKingService {

    private final BullKingImageMapper bullKingImageMapper;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.access-url}")
    private String accessUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addBullKing(BullKingDTO dto) {
        BullKing bullKing = new BullKing();
        bullKing.setDescription(dto.getDescription());
        bullKing.setBattleRecord(dto.getBattleRecord());
        save(bullKing);

        // 保存图片
        saveImages(bullKing.getId(), dto.getImages());
        return bullKing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBullKing(Long id, BullKingDTO dto) {
        BullKing bullKing = getById(id);
        if (bullKing == null) {
            throw new BusinessException("牛王不存在");
        }
        bullKing.setDescription(dto.getDescription());
        bullKing.setBattleRecord(dto.getBattleRecord());
        updateById(bullKing);

        // 删除未保留的旧图片
        List<BullKingImage> oldImages = bullKingImageMapper.selectList(
                new LambdaQueryWrapper<BullKingImage>().eq(BullKingImage::getBullKingId, id));
        List<String> retained = dto.getRetainedUrls() != null ? dto.getRetainedUrls() : List.of();
        for (BullKingImage oldImg : oldImages) {
            if (!retained.contains(accessUrl + oldImg.getImageUrl())) {
                bullKingImageMapper.deleteById(oldImg.getId());
            }
        }

        // 保存新上传的图片
        saveImages(id, dto.getImages());
    }

    @Override
    public BullKingVO getBullKingDetail(Long id) {
        BullKing bullKing = getById(id);
        if (bullKing == null) {
            throw new BusinessException("牛王不存在");
        }
        BullKingVO vo = new BullKingVO();
        vo.setId(bullKing.getId());
        vo.setDescription(bullKing.getDescription());
        vo.setBattleRecord(bullKing.getBattleRecord());
        vo.setCreateTime(bullKing.getCreateTime());
        vo.setUpdateTime(bullKing.getUpdateTime());

        // 加载图片列表
        List<BullKingImage> images = bullKingImageMapper.selectList(
                new LambdaQueryWrapper<BullKingImage>().eq(BullKingImage::getBullKingId, id)
                        .orderByAsc(BullKingImage::getSortOrder));
        List<BullKingVO.BullKingImageVO> imageVOs = images.stream().map(img -> {
            BullKingVO.BullKingImageVO imageVO = new BullKingVO.BullKingImageVO();
            imageVO.setId(img.getId());
            imageVO.setImageUrl(accessUrl + img.getImageUrl());
            imageVO.setSortOrder(img.getSortOrder());
            return imageVO;
        }).collect(Collectors.toList());
        vo.setImages(imageVOs);
        return vo;
    }

    @Override
    public PageResult<BullKingVO> pageBullKing(String keyword, long page, long size) {
        LambdaQueryWrapper<BullKing> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(BullKing::getDescription, keyword)
                    .or().like(BullKing::getBattleRecord, keyword));
        }
        wrapper.orderByDesc(BullKing::getCreateTime);

        Page<BullKing> pageParam = new Page<>(page, size);
        Page<BullKing> result = page(pageParam, wrapper);

        List<BullKingVO> voList = result.getRecords().stream().map(bk -> {
            BullKingVO vo = new BullKingVO();
            vo.setId(bk.getId());
            vo.setDescription(bk.getDescription());
            vo.setBattleRecord(bk.getBattleRecord());
            vo.setCreateTime(bk.getCreateTime());
            vo.setUpdateTime(bk.getUpdateTime());

            // 加载每行的首张图片用于列表展示和编辑回显
            List<BullKingImage> images = bullKingImageMapper.selectList(
                    new LambdaQueryWrapper<BullKingImage>()
                            .eq(BullKingImage::getBullKingId, bk.getId())
                            .orderByAsc(BullKingImage::getSortOrder));
            List<BullKingVO.BullKingImageVO> imageVOs = images.stream().map(img -> {
                BullKingVO.BullKingImageVO imageVO = new BullKingVO.BullKingImageVO();
                imageVO.setId(img.getId());
                imageVO.setImageUrl(accessUrl + img.getImageUrl());
                imageVO.setSortOrder(img.getSortOrder());
                return imageVO;
            }).collect(Collectors.toList());
            vo.setImages(imageVOs);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBullKing(Long id) {
        removeById(id);
        // 逻辑删除关联图片
        LambdaQueryWrapper<BullKingImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BullKingImage::getBullKingId, id);
        bullKingImageMapper.delete(wrapper);
    }

    /**
     * 保存牛王图片到本地磁盘
     */
    private void saveImages(Long bullKingId, MultipartFile[] images) {
        if (images == null || images.length == 0) return;

        File dir = new File(uploadPath, "/bullking/" + bullKingId);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        for (int i = 0; i < images.length; i++) {
            MultipartFile file = images[i];
            if (file == null || file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String fileName = "img_" + (i + 1) + ext;
            String filePath = "/bullking/" + bullKingId + "/" + fileName;

            try {
                file.transferTo(new File(dir, fileName));
            } catch (IOException e) {
                throw new BusinessException("图片保存失败: " + e.getMessage());
            }

            BullKingImage image = new BullKingImage();
            image.setBullKingId(bullKingId);
            image.setImageUrl(filePath);
            image.setSortOrder(i);
            bullKingImageMapper.insert(image);
        }
    }
}
