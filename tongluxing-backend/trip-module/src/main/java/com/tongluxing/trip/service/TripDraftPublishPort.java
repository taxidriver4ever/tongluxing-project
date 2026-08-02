package com.tongluxing.trip.service;

import java.util.List;

import com.tongluxing.user.vo.TeamMatchVO;
import com.tongluxing.user.vo.TripDraftVO;

/**
 * 行程草稿发布端口。
 *
 * <p>trip-module 只负责草稿状态管理，实际发布为行程或车队由应用层适配器完成。</p>
 */
public interface TripDraftPublishPort {

    /**
     * 发布草稿并返回发布结果。
     */
    PublishOutcome publish(Long userId, TripDraftVO draft, String publishType, String bizId);

    /**
     * 基于草稿内容查询推荐车队。
     */
    List<TeamMatchVO> recommendTeams(Long userId, TripDraftVO draft, int page, int size);

    /**
     * 草稿发布结果。
     */
    record PublishOutcome(Long tripId, Long publishedId) {
    }
}
