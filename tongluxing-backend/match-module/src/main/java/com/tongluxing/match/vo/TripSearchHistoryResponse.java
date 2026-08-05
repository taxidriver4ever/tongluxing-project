package com.tongluxing.match.vo;

/** App 行程搜索页历史记录。 */
public record TripSearchHistoryResponse(
        String historyId,
        String keyword,
        String searchType,
        String updatedAt
) {
}
