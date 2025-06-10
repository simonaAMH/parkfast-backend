package com.example.licenta.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTasksService {

    private final ReservationService reservationService;

    @Autowired
    public ScheduledTasksService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // daily at 6 AM
    @Scheduled(cron = "0 0 6 * * ?")
    public void cleanupExpiredTokens() {
        reservationService.cleanupExpiredGuestAccessTokens();
    }
}