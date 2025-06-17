package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.DTOs.UpdateAvailabilityRequestDTO;
import com.example.licenta.Services.AvailabilityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/availability")
public class ParkingLotAvailabilityController {

    private final AvailabilityService availabilityService;
    private static final Logger logger = LoggerFactory.getLogger(ParkingLotAvailabilityController.class);

    @Autowired
    public ParkingLotAvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{parkingLotId}")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> getParkingLotAvailability(@PathVariable String parkingLotId) {
        logger.info("Request received to get current availability for parking lot ID {}", parkingLotId);
        ParkingLotDTO parkingLotStatus = availabilityService.getCurrentAvailability(parkingLotId);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Current parking lot availability retrieved successfully.",
                parkingLotStatus
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sensor-update/{parkingLotId}")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> updateAvailabilityFromExternalSystem(
            @PathVariable String parkingLotId,
            @Valid @RequestBody UpdateAvailabilityRequestDTO requestDTO) {
        logger.info("Received absolute availability update for parking lot ID {}: {} empty spaces", parkingLotId, requestDTO.getEmptySpaces());
        ParkingLotDTO updatedParkingLot = availabilityService.updateSpotsAvailable(parkingLotId, requestDTO.getEmptySpaces());
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Parking lot availability updated successfully from sensor/external system.",
                updatedParkingLot
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{parkingLotId}/increment")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> incrementAvailableSpots(@PathVariable String parkingLotId) {
        logger.info("Received request to increment available spots for parking lot ID {}", parkingLotId);
        ParkingLotDTO updatedParkingLot = availabilityService.incrementAvailableSpots(parkingLotId);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Available spots incremented successfully for parking lot.",
                updatedParkingLot
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{parkingLotId}/decrement")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> decrementAvailableSpots(@PathVariable String parkingLotId) {
        logger.info("Received request to decrement available spots for parking lot ID {}", parkingLotId);
        ParkingLotDTO updatedParkingLot = availabilityService.decrementAvailableSpots(parkingLotId);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Available spots decremented successfully for parking lot.",
                updatedParkingLot
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{parkingLotId}/ai-poll")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> manualAiPoll(@PathVariable String parkingLotId) {
        logger.info("Manual AI Analytics polling requested for parking lot ID: {}", parkingLotId);

        try {
            ParkingLotDTO updatedParkingLot = availabilityService.manualPollAiAnalytics(parkingLotId);

            if (updatedParkingLot != null) {
                ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                        true,
                        HttpStatus.OK.value(),
                        "AI Analytics polling completed successfully for parking lot.",
                        updatedParkingLot
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                        false,
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Failed to poll AI Analytics for the specified parking lot.",
                        null
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            logger.error("Error during manual AI Analytics polling for parking lot ID: {}", parkingLotId, e);
            ApiResponse<ParkingLotDTO> response = new ApiResponse<>(
                    false,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "An error occurred while polling AI Analytics: " + e.getMessage(),
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}