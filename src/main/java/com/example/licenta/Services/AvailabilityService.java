package com.example.licenta.Services;

import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Mappers.ParkingLotMapper;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Enum.ParkingLot.AvailabilityTrackingMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class AvailabilityService {

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingLotMapper parkingLotMapper;
    private final RestTemplate restTemplate; // Ensure this is configured as a bean
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityService.class);

    @Value("${ai.analytics.base.url:http://localhost:8081/ai-analytics}")
    private String aiAnalyticsBaseUrl;

    @Value("${ai.analytics.polling.enabled:true}")
    private boolean aiPollingEnabled;

    private AvailabilityService self; // For self-injection

    @Autowired
    public AvailabilityService(ParkingLotRepository parkingLotRepository,
                               ParkingLotMapper parkingLotMapper,
                               RestTemplate restTemplate) {
        this.parkingLotRepository = parkingLotRepository;
        this.parkingLotMapper = parkingLotMapper;
        this.restTemplate = restTemplate;
    }

    @Autowired
    public void setSelf(@Lazy AvailabilityService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public ParkingLotDTO getCurrentAvailability(String parkingLotId) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId));
        return parkingLotMapper.toDTO(parkingLot);
    }

    @Transactional
    public ParkingLotDTO updateSpotsAvailable(String parkingLotId, int emptySpaces) {
        if (emptySpaces < 0) {
            throw new InvalidDataException("Number of empty spaces cannot be negative.");
        }

        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId));

        Integer totalSpots = parkingLot.getTotalSpots();

        if (totalSpots == null) {
            throw new InvalidDataException("Total spots for parking lot " + parkingLotId + " is not configured.");
        }

        if (emptySpaces > totalSpots) {
            logger.warn("Number of empty spaces ({}) reported for parking lot {} exceeds total capacity ({}). Clamping to total capacity.",
                    emptySpaces, parkingLotId, totalSpots);
            emptySpaces = totalSpots; // Clamp to total capacity
        }

        Integer oldSpotsAvailable = parkingLot.getSpotsAvailable();
        parkingLot.setSpotsAvailable(emptySpaces);
        parkingLot.setUpdatedAt(OffsetDateTime.now());

        ParkingLot updatedParkingLot = parkingLotRepository.save(parkingLot);

        logger.info("Successfully updated available spots for parking lot ID {}. Old: {}, New: {}. Total capacity: {}",
                parkingLotId,
                oldSpotsAvailable != null ? oldSpotsAvailable.toString() : "N/A",
                emptySpaces,
                totalSpots);

        return parkingLotMapper.toDTO(updatedParkingLot);
    }

    @Transactional
    public ParkingLotDTO incrementAvailableSpots(String parkingLotId) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId));

        Integer currentAvailable = parkingLot.getSpotsAvailable();
        Integer totalSpots = parkingLot.getTotalSpots();

        if (currentAvailable == null) {
            logger.warn("Available spots count is not initialized for parking lot {}. Assuming 0 if total spots is known.", parkingLotId);
            currentAvailable = 0;
        }

        if (totalSpots == null) {
            logger.warn("Parking lot {} has null totalSpots. Incrementing available spots without capacity check.", parkingLotId);
            parkingLot.setSpotsAvailable(currentAvailable + 1);
        } else if (currentAvailable < totalSpots) {
            parkingLot.setSpotsAvailable(currentAvailable + 1);
        } else {
            logger.warn("Cannot increment available spots for parking lot ID {}: already at full capacity ({} available, {} total).",
                    parkingLotId, currentAvailable, totalSpots);
        }
        parkingLot.setUpdatedAt(OffsetDateTime.now());
        ParkingLot updatedParkingLot = parkingLotRepository.save(parkingLot);
        logger.info("Incremented available spots for parking lot ID {}. New count: {}. Total capacity: {}",
                parkingLotId, updatedParkingLot.getSpotsAvailable(), totalSpots != null ? totalSpots.toString() : "N/A");
        return parkingLotMapper.toDTO(updatedParkingLot);
    }

    @Transactional
    public ParkingLotDTO decrementAvailableSpots(String parkingLotId) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId));

        Integer currentAvailable = parkingLot.getSpotsAvailable();

        if (currentAvailable == null) {
            logger.warn("Available spots count is not initialized for parking lot {}. Cannot decrement.", parkingLotId);
            throw new InvalidDataException("Available spots count is not initialized for this parking lot.");
        }

        if (currentAvailable > 0) {
            parkingLot.setSpotsAvailable(currentAvailable - 1);
        } else {
            logger.warn("Cannot decrement available spots for parking lot ID {}: already 0 available spots.", parkingLotId);
        }
        parkingLot.setUpdatedAt(OffsetDateTime.now());
        ParkingLot updatedParkingLot = parkingLotRepository.save(parkingLot);
        logger.info("Decremented available spots for parking lot ID {}. New count: {}. Total capacity: {}",
                parkingLotId, updatedParkingLot.getSpotsAvailable(), parkingLot.getTotalSpots() != null ? parkingLot.getTotalSpots().toString() : "N/A");
        return parkingLotMapper.toDTO(updatedParkingLot);
    }

    @Scheduled(fixedRateString = "${ai.analytics.polling.fixedRate:300000}") // 5 minutes default
    public void pollAiAnalyticsForAllParkingLots() {
        if (!aiPollingEnabled) {
            logger.debug("AI Analytics polling is disabled.");
            return;
        }

        logger.info("Starting AI Analytics polling cycle for all eligible parking lots.");
        List<ParkingLot> eligibleParkingLots = parkingLotRepository.findEligibleForAiPolling(AvailabilityTrackingMethod.CAMERA_AI);

        if (eligibleParkingLots.isEmpty()) {
            logger.info("No parking lots are currently eligible for AI Analytics polling.");
            return;
        }

        logger.info("Found {} parking lots eligible for AI Analytics polling.", eligibleParkingLots.size());

        eligibleParkingLots.forEach(parkingLot -> {
            try {
                CompletableFuture.runAsync(() -> self.pollSingleParkingLotAi(parkingLot.getId()));
            } catch (Exception e) {
                logger.error("Failed to submit async polling task for parking lot ID {}: {}", parkingLot.getId(), e.getMessage(), e);
            }
        });
    }

    @Transactional
    public void pollSingleParkingLotAi(String parkingLotId) {
        ParkingLot parkingLot = null;
        try {
            logger.debug("Polling AI Analytics for parking lot ID: {}", parkingLotId);

            parkingLot = parkingLotRepository.findById(parkingLotId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId + " during AI poll."));

            if (!isEligibleForAiPolling(parkingLot)) {
                logger.debug("Parking lot ID {} is not eligible for AI polling.", parkingLotId);
                return;
            }

            String endpoint = aiAnalyticsBaseUrl + "/parking-spots";
            logger.debug("Calling AI analytics endpoint: {} for parking lot: {}", endpoint, parkingLot.getName());


            ResponseEntity<Integer> response = restTemplate.getForEntity(endpoint, Integer.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                int detectedEmptySpots = response.getBody();
                logger.info("AI Analytics response for parking lot ID {}: {} empty spots.", parkingLotId, detectedEmptySpots);
                // This call to a protected method within the same class will participate in the current transaction
                updateFromAiAnalytics(parkingLot, detectedEmptySpots);
            } else {
                logger.warn("Received non-OK response or null body from AI Analytics for parking lot ID: {}. Status: {}, Body: {}",
                        parkingLotId, response.getStatusCode(), response.getBody());
            }

        } catch (ResourceNotFoundException e) {
            logger.warn("ResourceNotFoundException during AI poll for parking lot {}: {}", parkingLotId, e.getMessage());
        } catch (RestClientException e) {
            logger.error("RestClientException while polling AI Analytics for parking lot ID: {}. Error: {}",
                    parkingLotId, e.getMessage()); // Consider not logging full stack trace for common client errors unless in DEBUG
        } catch (Exception e) {
            logger.error("Unexpected error while polling AI Analytics for parking lot ID: {}. Error: {}",
                    parkingLotId, e.getMessage(), e); // Log full stack trace for unexpected errors
        }
    }

    @Transactional
    public ParkingLotDTO manualPollAiAnalytics(String parkingLotId) {
        logger.info("Manual AI Analytics polling triggered for parking lot ID: {}", parkingLotId);
        try {
            // Call through the self-injected proxy
            self.pollSingleParkingLotAi(parkingLotId);
            // Call through self for consistency, though its own @Transactional(readOnly=true) would be respected
            return self.getCurrentAvailability(parkingLotId);
        } catch (Exception e) {
            logger.error("Error during manual AI Analytics poll for parking lot ID {}: {}", parkingLotId, e.getMessage(), e);
            return null; // Or throw a custom exception / return DTO indicating error
        }
    }

    private boolean isEligibleForAiPolling(ParkingLot parkingLot) {
        if (parkingLot == null || parkingLot.getParkingAvailabilityMethod() == null) {
            logger.warn("Eligibility check failed: parkingLot or its availability method is null for ID: {}", parkingLot != null ? parkingLot.getId() : "UNKNOWN");
            return false;
        }
        return !parkingLot.isHasExistingAvailabilitySystem() &&
                parkingLot.isSharedWithNonAppUsers() &&
                parkingLot.getParkingAvailabilityMethod() == AvailabilityTrackingMethod.CAMERA_AI;
    }

    @Transactional
    protected void updateFromAiAnalytics(ParkingLot parkingLot, int emptySpots) {
        if (emptySpots < 0) {
            logger.warn("AI Analytics reported negative empty spots ({}) for parking lot {}. Ignoring update.", emptySpots, parkingLot.getId());
            return;
        }

        Integer totalSpots = parkingLot.getTotalSpots();
        if (totalSpots == null) {
            logger.error("Cannot update parking lot {} from AI Analytics: totalSpots is null. Aborting update for this lot.", parkingLot.getId());
            return; // Critical information missing
        }

        if (emptySpots > totalSpots) {
            logger.warn("AI Analytics reported {} empty spots for parking lot {}, which exceeds total spots ({}). Clamping to total spots.",
                    emptySpots, parkingLot.getId(), totalSpots);
            emptySpots = totalSpots;
        }

        Integer oldSpotsAvailable = parkingLot.getSpotsAvailable();

        if (Objects.equals(oldSpotsAvailable, emptySpots)) {
            parkingLot.setUpdatedAt(OffsetDateTime.now());
            parkingLotRepository.save(parkingLot);
            return;
        }

        parkingLot.setSpotsAvailable(emptySpots);
        parkingLot.setUpdatedAt(OffsetDateTime.now());
        parkingLotRepository.save(parkingLot);

        logger.info("Successfully updated parking lot {} from AI Analytics. Old: {}, New: {}, Total: {}",
                parkingLot.getId(),
                oldSpotsAvailable != null ? oldSpotsAvailable.toString() : "N/A",
                emptySpots,
                totalSpots);
    }
}