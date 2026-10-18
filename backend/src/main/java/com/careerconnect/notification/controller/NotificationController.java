package com.careerconnect.notification.controller;

import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.notification.dto.NotificationDto;
import com.careerconnect.notification.dto.NotificationSummaryResponse;
import com.careerconnect.notification.service.NotificationService;
import com.careerconnect.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Lists notifications for the authenticated user with pagination and optional unread filter.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unread
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageRequestDto pageRequest = new PageRequestDto(page, size);
        NotificationSummaryResponse response = notificationService.getNotifications(userId, pageRequest, unread);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Marks a specific notification as read.
     * Accessible via PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markNotificationAsRead(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationDto notification = notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success(notification));
    }

    /**
     * Marks a specific notification as read.
     * Accessible via PATCH /api/notifications/{id} with optional body.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationDto>> updateNotification(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        NotificationDto notification = notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success(notification));
    }

    /**
     * Marks all unread notifications as read for the authenticated user.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllNotificationsAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("markedReadCount", count)));
    }
}
