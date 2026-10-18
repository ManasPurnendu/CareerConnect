package com.careerconnect.notification.service;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.notification.dto.NotificationDto;
import com.careerconnect.notification.dto.NotificationSummaryResponse;
import com.careerconnect.notification.model.NotificationType;
import com.careerconnect.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates an in-app notification.
     */
    @Transactional
    public Long createNotification(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String resourceType,
            Long resourceId
    ) {
        if (userId == null) {
            log.warn("Cannot create notification: userId is null");
            return null;
        }
        Long id = notificationRepository.insert(
                userId,
                type,
                title,
                body,
                resourceType,
                resourceId,
                Instant.now()
        );
        log.info("Created notification ID {} for user ID {} (type: {})", id, userId, type);
        return id;
    }

    /**
     * Sends an in-app notification to a candidate when their application status changes.
     */
    @Transactional
    public void notifyApplicationStatusChange(
            Long candidateUserId,
            Long applicationId,
            String jobTitle,
            ApplicationStatus newStatus
    ) {
        if (candidateUserId == null) {
            log.warn("Cannot send status notification: candidateUserId is null for application {}", applicationId);
            return;
        }
        String title = "Application Status Updated";
        String body = "Your application for \"" + (jobTitle != null ? jobTitle : "the position") + "\" has been updated to " + newStatus + ".";

        createNotification(
                candidateUserId,
                NotificationType.APPLICATION_STATUS_CHANGED,
                title,
                body,
                "APPLICATION",
                applicationId
        );
    }

    /**
     * Notifies all candidates with active applications when a job is closed.
     */
    @Transactional
    public int notifyJobClosure(Long jobId, String jobTitle) {
        if (jobId == null) {
            return 0;
        }
        List<Long> candidateUserIds = notificationRepository.findCandidateUserIdsByJobId(jobId);
        if (candidateUserIds.isEmpty()) {
            log.debug("No active applicants to notify for closed job ID {}", jobId);
            return 0;
        }

        String title = "Job Listing Closed";
        String body = "The job listing \"" + (jobTitle != null ? jobTitle : "Position") + "\" has been closed.";

        int count = 0;
        for (Long userId : candidateUserIds) {
            createNotification(
                    userId,
                    NotificationType.JOB_CLOSED,
                    title,
                    body,
                    "JOB",
                    jobId
            );
            count++;
        }

        log.info("Notified {} applicants of job closure for job ID {}", count, jobId);
        return count;
    }

    /**
     * Retrieves paginated notifications for an authenticated user with unread count.
     */
    public NotificationSummaryResponse getNotifications(Long userId, PageRequestDto pageRequest, Boolean unreadOnly) {
        long totalElements = notificationRepository.countByUserId(userId, unreadOnly);
        long unreadCount = notificationRepository.countUnreadByUserId(userId);

        List<NotificationDto> items = notificationRepository.findByUserId(
                userId,
                unreadOnly,
                pageRequest.getSize(),
                (int) pageRequest.getOffset()
        );

        return NotificationSummaryResponse.of(
                items,
                pageRequest.getPage(),
                pageRequest.getSize(),
                totalElements,
                unreadCount
        );
    }

    /**
     * Marks a single notification as read if it belongs to the authenticated user.
     * Returns generic 404 RESOURCE_NOT_FOUND if the notification does not exist or belongs to another user.
     */
    @Transactional
    public NotificationDto markAsRead(Long userId, Long notificationId) {
        NotificationDto notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification with ID " + notificationId + " not found.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!notification.read()) {
            notificationRepository.markAsRead(notificationId, userId, Instant.now());
            return notificationRepository.findByIdAndUserId(notificationId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Notification with ID " + notificationId + " not found.",
                            ErrorCode.RESOURCE_NOT_FOUND
                    ));
        }

        return notification;
    }

    /**
     * Marks all unread notifications as read for the authenticated user.
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }
}
