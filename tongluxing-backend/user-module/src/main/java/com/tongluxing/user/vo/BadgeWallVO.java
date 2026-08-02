package com.tongluxing.user.vo;

import java.util.List;

/**
 * 徽章墙返回对象。
 *
 * <p>earned 表示已获得徽章，locked 表示尚未获得但可展示的徽章。</p>
 *
 * @param earned 已获得徽章
 * @param locked 尚未获得的可见徽章
 */
public record BadgeWallVO(List<BadgeVO> earned, List<BadgeVO> locked) {
}

