package com.niuwang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuwang.common.response.PageResult;
import com.niuwang.model.dto.BullKingDTO;
import com.niuwang.model.entity.BullKing;
import com.niuwang.model.vo.BullKingVO;

/**
 * 牛王服务接口
 */
public interface BullKingService extends IService<BullKing> {

    /** 新增牛王(含图片) */
    Long addBullKing(BullKingDTO dto);

    /** 更新牛王(含图片) */
    void updateBullKing(Long id, BullKingDTO dto);

    /** 查询牛王详情 */
    BullKingVO getBullKingDetail(Long id);

    /** 分页查询牛王列表 */
    PageResult<BullKingVO> pageBullKing(String keyword, long page, long size);

    /** 删除牛王 */
    void deleteBullKing(Long id);
}
