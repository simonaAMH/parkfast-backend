package com.example.licenta.Services;

import com.example.licenta.DTOs.ClosestParkingLotInfoDTO;
import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.Enum.ParkingLot.ParkingLotStatus;
import com.example.licenta.Enum.ParkingLot.PaymentTiming;
import com.example.licenta.Exceptions.InvalidCredentialsException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ParkingLotMapper;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Repositories.UserRepository;
import com.example.licenta.Utils.LocationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ParkingLotService {

    private final ParkingLotRepository parkingLotRepository;
    private final UserRepository userRepository;
    private final ParkingLotMapper parkingLotMapper;
    private final ImageService imageService;

    @Autowired
    public ParkingLotService(
            ParkingLotRepository parkingLotRepository,
            UserRepository userRepository,
            ParkingLotMapper parkingLotMapper,
            ImageService imageService) {
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.parkingLotMapper = parkingLotMapper;
        this.imageService = imageService;
    }

    @Transactional
    public ParkingLot createParkingLot(ParkingLotDTO dto, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + ownerId));

        ParkingLot parkingLot = parkingLotMapper.toEntity(dto);
        parkingLot.setOwner(owner);
        parkingLot.setCreatedAt(OffsetDateTime.now());
        parkingLot.setUpdatedAt(OffsetDateTime.now());
        //parkingLot.setStatus(ParkingLotStatus.PENDING_APPROVAL);
        parkingLot.setStatus(ParkingLotStatus.ACTIVE);
        parkingLot.setSpotsAvailable(parkingLot.getTotalSpots());

        return parkingLotRepository.save(parkingLot);
    }

    @Transactional
    public ParkingLot updateParkingLot(Long parkingLotId, ParkingLotDTO dto, Long userId) {
        ParkingLot existingParkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with ID: " + parkingLotId));

        if (!existingParkingLot.getOwner().getId().equals(userId)) {
            throw new InvalidCredentialsException("You don't have permission to update this parking lot");
        }

        parkingLotMapper.updateEntityFromDTO(dto, existingParkingLot);
        existingParkingLot.setUpdatedAt(OffsetDateTime.now());

        return parkingLotRepository.save(existingParkingLot);
    }

    @Transactional
    public void deleteParkingLot(Long parkingLotId, Long userId) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with ID: " + parkingLotId));

        if (!parkingLot.getOwner().getId().equals(userId)) {
            throw new InvalidCredentialsException("You don't have permission to delete this parking lot");
        }

        if (parkingLot.getPhotos() != null) {
            for (String photoUrl : parkingLot.getPhotos()) {
                try {
                    imageService.deleteImage(photoUrl);
                } catch (Exception e) {
                    // Log but continue with deletion
                    System.err.println("Failed to delete image: " + photoUrl + " - " + e.getMessage());
                }
            }
        }

        parkingLotRepository.delete(parkingLot);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> findNearbyParkingLots(double latitude, double longitude, double radiusInKm, Pageable pageable) {

        return parkingLotRepository.findNearbyParkingLots(
                latitude, longitude, radiusInKm, pageable);
    }

    @Transactional(readOnly = true)
    public ParkingLot getParkingLotById(Long id) {
        return parkingLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getAllParkingLots(Pageable pageable) {
        return parkingLotRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getParkingLotsByOwner(User owner, Pageable pageable) {
        return parkingLotRepository.findByOwner(owner, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getParkingLotsAllowingDirectPayment(Pageable pageable) {
        return parkingLotRepository.findByAllowDirectPaymentTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getParkingLotsAllowingStandardReservations(Pageable pageable) {
        return parkingLotRepository.findByAllowReservationsTrueAndPaymentTimingEquals(PaymentTiming.BEFORE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getParkingLotsAllowingPayForUsageReservations(Pageable pageable) {
        return parkingLotRepository.findByAllowReservationsTrueAndPaymentTimingEquals(PaymentTiming.AFTER, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ParkingLot> getParkingLotsAllowingAnyReservation(Pageable pageable) {
        return parkingLotRepository.findByAllowReservationsTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<ParkingLot> findParkingLotAtUserLocation(double userLatitude, double userLongitude, double userAccuracyInMeters) {
        List<ParkingLot> allParkingLots = parkingLotRepository.findByStatus(ParkingLotStatus.ACTIVE);

        for (ParkingLot lot : allParkingLots) {
            if (lot.getGpsCoordinates() == null || lot.getGpsCoordinates().trim().isEmpty()) {
                continue;
            }

            String[] coords = lot.getGpsCoordinates().split(",");
            if (coords.length != 2) {
                System.err.println("Invalid GPS coordinates format for parking lot ID " + lot.getId() + ": " + lot.getGpsCoordinates());
                continue;
            }

            try {
                double lotLatitude = Double.parseDouble(coords[0].trim());
                double lotLongitude = Double.parseDouble(coords[1].trim());

                double distanceToLotCenter = LocationUtils.calculateDistanceInMeters(userLatitude, userLongitude, lotLatitude, lotLongitude);

                if (distanceToLotCenter <= userAccuracyInMeters) {
                    return Optional.of(lot);
                }
            } catch (NumberFormatException e) {
                System.err.println("Could not parse GPS coordinates for parking lot ID " + lot.getId() + ": " + lot.getGpsCoordinates() + " - " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ClosestParkingLotInfoDTO> findClosestParkingLotInProximity(
            double userLatitude, double userLongitude, double proximityRadiusInMeters) {

        List<ParkingLot> allActiveLots = parkingLotRepository.findByStatus(ParkingLotStatus.ACTIVE);
        ParkingLot closestLot = null;
        double minDistance = Double.MAX_VALUE;

        for (ParkingLot lot : allActiveLots) {
            if (lot.getGpsCoordinates() == null || lot.getGpsCoordinates().trim().isEmpty()) {
                continue;
            }
            String[] coords = lot.getGpsCoordinates().split(",");
            if (coords.length != 2) {
                System.err.println("ClosestProximity Check: Invalid GPS for lot ID " + lot.getId() + ": " + lot.getGpsCoordinates());
                continue;
            }
            try {
                double lotLatitude = Double.parseDouble(coords[0].trim());
                double lotLongitude = Double.parseDouble(coords[1].trim());
                double distance = LocationUtils.calculateDistanceInMeters(userLatitude, userLongitude, lotLatitude, lotLongitude);

                if (distance <= proximityRadiusInMeters) { // Check if within defined proximity
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestLot = lot;
                    }
                }
            } catch (NumberFormatException e) {
                System.err.println("ClosestProximity Check: Parse error for lot ID " + lot.getId() + ": " + lot.getGpsCoordinates() + " - " + e.getMessage());
            }
        }

        if (closestLot != null) {
            return Optional.of(new ClosestParkingLotInfoDTO(closestLot.getId(), minDistance));
        }
        return Optional.empty();
    }
}