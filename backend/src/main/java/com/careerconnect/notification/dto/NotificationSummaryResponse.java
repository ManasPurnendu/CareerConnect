package com.careerconnect.notification.dto;

import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.PaginationMetadata;

import java.util.List;

/**
 * Paginated notification summary response including total unread count.
 */
public class NotificationSummaryResponse extends PageResult<NotificationDto> {

    private final long unreadCount;

    public NotificationSummaryResponse(List<NotificationDto> items, PaginationMetadata pagination, long unreadCount) {
        super(items, pagination);
        this.unreadCount = unreadCount;
    }

    public static NotificationSummaryResponse of(
            List<NotificationDto> items,
            int page,
            int size,
            long totalElements,
            long unreadCount
    ) {
        return new NotificationSummaryResponse(items, PaginationMetadata.of(page, size, totalElements), unreadCount);
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public long unreadCount() {
        return unreadCount;
    }
}
