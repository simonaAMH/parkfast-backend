package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.Services.ParkingLotAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/access")
public class ParkingLotAccessController {

    private final ParkingLotAccessService parkingLotAccessService;

    @Autowired
    public ParkingLotAccessController(ParkingLotAccessService parkingLotAccessService) {
        this.parkingLotAccessService = parkingLotAccessService;
    }

    // 1. GPS Automated Check-in
    @PostMapping("/gps-checkin-user")
    public ResponseEntity<ApiResponse<Object>> gpsCheckInUser(
            @RequestParam String parkingLotId,
            @Nullable @RequestParam String userId) {
        parkingLotAccessService.gpsCheckInUser(userId, parkingLotId);
        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
                String.format("User %s GPS check-in successful for parking lot %s.", userId, parkingLotId), null);
        return ResponseEntity.ok(response);
    }

//    @PostMapping("/gps-checkin-guest")
//    public ResponseEntity<ApiResponse<Object>> gpsCheckInGuest(
//            @RequestParam String parkingLotId,
//            @RequestParam String deviceIdentifier) {
//        parkingLotAccessService.gpsCheckInGuest(deviceIdentifier, parkingLotId);
//        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
//                String.format("Guest with deviceIdentifier: %s GPS check-in successful for parking lot %s.", deviceIdentifier, parkingLotId), null);
//        return ResponseEntity.ok(response);
//    }

    // 1. GPS Automated Check-out
    @PostMapping("/gps-checkout-user")
    public ResponseEntity<ApiResponse<Object>> gpsCheckOutUser(
            @RequestParam String parkingLotId,
            @RequestParam String userId) {
        parkingLotAccessService.gpsCheckOutUser(userId, parkingLotId);
        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
                String.format("User %s GPS check-out successful from parking lot %s.", userId, parkingLotId), null);
        return ResponseEntity.ok(response);
    }

//    @PostMapping("/gps-checkout-guest")
//    public ResponseEntity<ApiResponse<Object>> gpsCheckOutGuest(
//            @RequestParam String parkingLotId,
//            @Nullable @RequestParam String deviceIdentifier) {
//        parkingLotAccessService.gpsCheckOutGuest(deviceIdentifier, parkingLotId);
//        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
//                String.format("Guest with deviceIdentifier: %s GPS check-out successful from parking lot %s.", deviceIdentifier, parkingLotId), null);
//        return ResponseEntity.ok(response);
//    }

    // 2. QR Scan
    @PostMapping("/qr-scan/{qrCodeData}")
    public ResponseEntity<ApiResponse<Object>> qrScan(@PathVariable String qrCodeData) {
        parkingLotAccessService.handleQrScan(qrCodeData);
        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(), "QR scanned successfully.", null);
        return ResponseEntity.ok(response);
    }

    // 3. Integration with Barrier System - Verify Entry
    @PostMapping("/barrier/verify-entry")
    public ResponseEntity<ApiResponse<Object>> barrierVerifyEntry(
            @RequestParam String parkingLotId,
            @RequestParam String plateNumber) {
        parkingLotAccessService.barrierVerifyEntry(plateNumber, parkingLotId);
        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
                String.format("Barrier entry approved for plate number %s at lot %s.", plateNumber, parkingLotId), null);
        return ResponseEntity.ok(response);
    }

    // 3. Integration with Barrier System - Verify Exit
    @PostMapping("/barrier/verify-exit")
    public ResponseEntity<ApiResponse<Object>> barrierVerifyExit(
            @RequestParam String parkingLotId,
            @RequestParam String plateNumber) {
        parkingLotAccessService.barrierVerifyExit(plateNumber, parkingLotId);
        ApiResponse<Object> response = new ApiResponse<>(true, HttpStatus.OK.value(),
                String.format("Barrier exit approved for plate number %s from lot %s.", plateNumber, parkingLotId), null);
        return ResponseEntity.ok(response);
    }
}