package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.DTOs.ClosestParkingLotInfoDTO;
import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.Enum.ParkingLot.PaymentTiming;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Mappers.ParkingLotMapper;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.User;
import com.example.licenta.Services.ParkingLotService;
import com.example.licenta.Services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

    private final ParkingLotService parkingLotService;
    private final UserService userService;
    private final ParkingLotMapper parkingLotMapper;

    @Autowired
    public ParkingLotController(
            ParkingLotService parkingLotService,
            UserService userService,
            ParkingLotMapper parkingLotMapper) {
        this.parkingLotService = parkingLotService;
        this.userService = userService;
        this.parkingLotMapper = parkingLotMapper;
    }

    @GetMapping("/check-location-proximity")
    public ResponseEntity<ApiResponse<ClosestParkingLotInfoDTO>> checkUserProximityToClosestLot(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "100.0") double proximityRadius) {

        if (proximityRadius <= 0) {
            throw new InvalidDataException("Proximity radius must be a positive value.");
        }

        Optional<ClosestParkingLotInfoDTO> closestLotInfoOpt = parkingLotService.findClosestParkingLotInProximity(latitude, longitude, proximityRadius);

        if (closestLotInfoOpt.isPresent()) {
            ApiResponse<ClosestParkingLotInfoDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Closest parking lot in proximity found.",
                    closestLotInfoOpt.get()
            );
            return ResponseEntity.ok(response);
        } else {
            ApiResponse<ClosestParkingLotInfoDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "User is not in proximity of any known active parking lot.",
                    null
            );
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/check-location-inside")
    public ResponseEntity<ApiResponse<Long>> checkUserLocationInsideParkingLot(
                                                                                @RequestParam double latitude,
                                                                                @RequestParam double longitude,
                                                                                @RequestParam double accuracy) {

        if (accuracy <= 0) {
            throw new InvalidDataException("Accuracy must be a positive value.");
        }

        Optional<ParkingLot> foundLotOpt = parkingLotService.findParkingLotAtUserLocation(latitude, longitude, accuracy);

        if (foundLotOpt.isPresent()) {
            Long parkingLotId = foundLotOpt.get().getId();
            ApiResponse<Long> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "User is inside this parking lot",
                    parkingLotId
            );
            return ResponseEntity.ok(response);
        } else {
            ApiResponse<Long> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "User is not inside any known active parking lot",
                    null
            );
            return ResponseEntity.ok(response);
        }
    }

        @PostMapping
    public ResponseEntity<ApiResponse<?>> createParkingLot(@Valid @RequestBody ParkingLotDTO parkingLotDTO) {
        ParkingLot parkingLot = parkingLotService.createParkingLot(parkingLotDTO, parkingLotDTO.getOwnerId());
        ParkingLotDTO responseDTO = parkingLotMapper.toDTO(parkingLot);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(true, HttpStatus.CREATED.value(), "Parking lot created successfully", responseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllParkingLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getAllParkingLots(pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lots retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ParkingLotDTO>> getParkingLotById(@PathVariable Long id) {
        ParkingLot parkingLot = parkingLotService.getParkingLotById(id);
        ParkingLotDTO responseDTO = parkingLotMapper.toDTO(parkingLot);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lot retrieved successfully", responseDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getParkingLotsByUser( // Return type changed to Map
                                                                                  @PathVariable Long userId,
                                                                                  @RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "10") int size,
                                                                                  @RequestParam(defaultValue = "updatedAt") String sortBy,
                                                                                  @RequestParam(defaultValue = "desc") String direction) {

        User user = userService.findById(userId)
                .orElseThrow(() -> new InvalidDataException("User not found with ID: " + userId));

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getParkingLotsByOwner(user, pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "User's parking lots retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNearbyParkingLots(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radius,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ParkingLot> parkingLotsPage = parkingLotService.findNearbyParkingLots(latitude, longitude, radius, pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Nearby parking lots retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> updateParkingLot(
            @PathVariable Long id,
            @Valid @RequestBody ParkingLotDTO parkingLotDTO) {
        ParkingLot parkingLot = parkingLotService.getParkingLotById(id);
        ParkingLot updatedParkingLot = parkingLotService.updateParkingLot(id, parkingLotDTO, parkingLot.getOwner().getId());
        ParkingLotDTO responseDTO = parkingLotMapper.toDTO(updatedParkingLot);
        ApiResponse<ParkingLotDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lot updated successfully", responseDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteParkingLot(@PathVariable Long id) {
        ParkingLot parkingLot = parkingLotService.getParkingLotById(id);
        parkingLotService.deleteParkingLot(id, parkingLot.getOwner().getId());
        ApiResponse<Void> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lot deleted successfully", null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/direct-payment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDirectPaymentLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getParkingLotsAllowingDirectPayment(pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lots allowing direct payment retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reservable/standard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStandardReservableLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getParkingLotsAllowingStandardReservations(pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lots allowing standard reservations retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reservable/pay-for-usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPayForUsageReservableLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getParkingLotsAllowingPayForUsageReservations(pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lots allowing pay-for-usage reservations retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reservable/any")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnyReservableLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<ParkingLot> parkingLotsPage = parkingLotService.getParkingLotsAllowingAnyReservation(pageable);

        List<ParkingLotDTO> parkingLotDTOs = parkingLotsPage.getContent().stream()
                .map(parkingLotMapper::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("parkingLots", parkingLotDTOs);
        responseData.put("currentPage", parkingLotsPage.getNumber());
        responseData.put("totalItems", parkingLotsPage.getTotalElements());
        responseData.put("totalPages", parkingLotsPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Parking lots allowing any reservation retrieved successfully", responseData);
        return ResponseEntity.ok(response);
    }


}