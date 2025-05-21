package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.DTOs.CreateNotificationDTO;
import com.example.licenta.DTOs.NotificationDTO;
import com.example.licenta.Enum.Notification.NotificationType;
import com.example.licenta.Models.Notification;
import com.example.licenta.Services.NotificationService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/users/{userId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private NotificationDTO convertToDto(Notification notification) {
        if (notification == null) return null;
        return new NotificationDTO(
                notification.getId(),
                notification.getUser().getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getType() != null ? notification.getType().getValue() : null,
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }


    @PostMapping
    public ResponseEntity<ApiResponse<NotificationDTO>> createNotification(
            @PathVariable String userId,
            @Valid @RequestBody CreateNotificationDTO createDto) {

        NotificationType notificationType = null;
        if (createDto.getType() != null && !createDto.getType().trim().isEmpty()) {
            notificationType = NotificationType.fromString(createDto.getType());
        }

        Notification notification = notificationService.createNotification(
                userId,
                createDto.getTitle(),
                createDto.getBody(),
                notificationType,
                createDto.getReferenceId()
        );
        NotificationDTO notificationDto = convertToDto(notification);
        ApiResponse<NotificationDTO> response = new ApiResponse<>(
                true, HttpStatus.CREATED.value(), "Notification created successfully", notificationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> getNotificationsForUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Notification> notificationPage = notificationService.getNotificationsForUser(userId, pageable);
        Page<NotificationDTO> dtoPage = notificationPage.map(this::convertToDto);

        ApiResponse<Page<NotificationDTO>> response = new ApiResponse<>(
                true, HttpStatus.OK.value(), "Notifications retrieved successfully", dtoPage);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationDTO>> markNotificationAsRead(
            @PathVariable String userId,
            @PathVariable String notificationId) {

        Notification notification = notificationService.markNotificationAsRead(notificationId, userId);
        NotificationDTO notificationDto = convertToDto(notification);
        ApiResponse<NotificationDTO> response = new ApiResponse<>(
                true, HttpStatus.OK.value(), "Notification marked as read", notificationDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllNotificationsAsRead(
            @PathVariable String userId) {

        int updatedCount = notificationService.markAllNotificationsAsReadForUser(userId);
        Map<String, Object> responseData = Map.of(
                "message", "All unread notifications marked as read.",
                "updatedCount", updatedCount
        );
        ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                true, HttpStatus.OK.value(), "All notifications marked as read for user " + userId, responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadNotificationCount(@PathVariable String userId) {
        long count = notificationService.getUnreadNotificationCountForUser(userId);
        ApiResponse<Long> response = new ApiResponse<>(
                true, HttpStatus.OK.value(), "Unread notification count retrieved", count);
        return ResponseEntity.ok(response);
    }
}