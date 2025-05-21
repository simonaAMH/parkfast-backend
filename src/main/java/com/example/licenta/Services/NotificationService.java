package com.example.licenta.Services;

import com.example.licenta.Enum.Notification.NotificationType;
import com.example.licenta.Exceptions.AuthenticationException;
import com.example.licenta.Models.Notification;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.NotificationRepository;
import com.example.licenta.Repositories.UserRepository;
import com.example.licenta.Exceptions.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(String userId, String title, String body, NotificationType type, String referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotificationsForUser(String userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId + ". Cannot fetch notifications.");
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public Notification markNotificationAsRead(String notificationId, String userIdPerformingAction) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));

        if (!notification.getUser().getId().equals(userIdPerformingAction)) {
            throw new AuthenticationException("User not authorized to mark this notification as read.");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public int markAllNotificationsAsReadForUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId + ". Cannot mark all notifications as read.");
        }
        return notificationRepository.markAllAsReadForUser(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadNotificationCountForUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId + ". Cannot get unread count.");
        }
        return notificationRepository.countByUserIdAndIsReadIsFalse(userId);
    }
}