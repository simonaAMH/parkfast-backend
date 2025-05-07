package com.example.licenta.Mappers;

import com.example.licenta.DTOs.CustomHourIntervalDTO;
import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.DTOs.PriceIntervalDTO;
import com.example.licenta.Models.CustomHourInterval;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.PriceInterval;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ParkingLotMapper {

    public ParkingLotDTO toDTO(ParkingLot parkingLot) {
        if (parkingLot == null) return null;

        ParkingLotDTO dto = new ParkingLotDTO();

        dto.setId(parkingLot.getId());
        dto.setName(parkingLot.getName());
        dto.setAddress(parkingLot.getAddress());
        dto.setGpsCoordinates(parkingLot.getGpsCoordinates());
        dto.setDescription(parkingLot.getDescription());
        dto.setMaxVehicleHeight(parkingLot.getMaxVehicleHeight());
        dto.setTotalSpots(parkingLot.getTotalSpots());
        dto.setEvChargingSpots(parkingLot.getEvChargingSpots());
        dto.setDisabilitySpots(parkingLot.getDisabilitySpots());
        dto.setFamilySpots(parkingLot.getFamilySpots());
        dto.setTimeBeforeReservation(parkingLot.getTimeBeforeReservation());
        dto.setTimeAfterReservation(parkingLot.getTimeAfterReservation());
        dto.setMinParkingDuration(parkingLot.getMinParkingDuration());
        dto.setMaxParkingDuration(parkingLot.getMaxParkingDuration());
        dto.setMinParkingDurationUnit(parkingLot.getMinParkingDurationUnit());
        dto.setMaxParkingDurationUnit(parkingLot.getMaxParkingDurationUnit());
        dto.setFreeTimeMinutes(parkingLot.getFreeTimeMinutes());
        dto.setBankAccountName(parkingLot.getBankAccountName());
        dto.setBankAccountNumber(parkingLot.getBankAccountNumber());
        dto.setMaxExtensionTime(parkingLot.getMaxExtensionTime());
        dto.setExtensionPricingPercentage(parkingLot.getExtensionPricingPercentage());
        dto.setPreReservationCancelWindow(parkingLot.getPreReservationCancelWindow());
        dto.setPreReservationCancelFee(parkingLot.getPreReservationCancelFee());
        dto.setMidReservationCancelWindow(parkingLot.getMidReservationCancelWindow());
        dto.setMidReservationCancelFee(parkingLot.getMidReservationCancelFee());
        dto.setSpotsAvailable(parkingLot.getSpotsAvailable());
        dto.setCreatedAt(parkingLot.getCreatedAt());
        dto.setUpdatedAt(parkingLot.getUpdatedAt());

        dto.setLighted(parkingLot.isLighted());
        dto.setHasVideoSurveillance(parkingLot.isHasVideoSurveillance());
        dto.setAllowReservations(parkingLot.isAllowReservations());
        dto.setAllowDirectPayment(parkingLot.isAllowDirectPayment());
        dto.setRequireQrCode(parkingLot.isRequireQrCode());
        dto.setHasExistingAvailabilitySystem(parkingLot.isHasExistingAvailabilitySystem());
        dto.setSharedWithNonAppUsers(parkingLot.isSharedWithNonAppUsers());
        dto.setDisplayFees(parkingLot.isDisplayFees());
        dto.setHasTimeLimits(parkingLot.isHasTimeLimits());
        dto.setHasFreeTime(parkingLot.isHasFreeTime());
        dto.setAllowExtensions(parkingLot.isAllowExtensions());
        dto.setAllowCancellations(parkingLot.isAllowCancellations());
        dto.setAllowPreReservationCancellations(parkingLot.isAllowPreReservationCancellations());
        dto.setApplyPreCancelFee(parkingLot.isApplyPreCancelFee());
        dto.setAllowMidReservationCancellations(parkingLot.isAllowMidReservationCancellations());
        dto.setApplyMidCancelFee(parkingLot.isApplyMidCancelFee());

        // Direct enum mappings - simply set the enum values
        dto.setCategory(parkingLot.getCategory());
        dto.setSize(parkingLot.getSize());
        dto.setAvailability(parkingLot.getAvailability());
        dto.setPaymentTiming(parkingLot.getPaymentTiming());
        dto.setAccessVerificationMethod(parkingLot.getAccessVerificationMethod());
        dto.setTimeBeforeReservationUnit(parkingLot.getTimeBeforeReservationUnit());
        dto.setTimeAfterReservationUnit(parkingLot.getTimeAfterReservationUnit());
        dto.setParkingAvailabilityMethod(parkingLot.getParkingAvailabilityMethod());
        dto.setPricingType(parkingLot.getPricingType());
        dto.setExtensionPricingModel(parkingLot.getExtensionPricingModel());
        dto.setStatus(parkingLot.getStatus());

        // Direct collection of enums
        dto.setTypes(parkingLot.getTypes());

        // Photos (string collection)
        if (parkingLot.getPhotos() != null) {
            dto.setPhotos(new ArrayList<>(parkingLot.getPhotos()));
        }

        // Custom Hour Intervals
        if (parkingLot.getCustomHourIntervals() != null) {
            List<CustomHourIntervalDTO> customHourIntervalDTOs = parkingLot.getCustomHourIntervals().stream()
                    .map(this::toCustomHourIntervalDTO)
                    .collect(Collectors.toList());
            dto.setCustomHourIntervals(customHourIntervalDTOs);
        }

        // Price Intervals
        if (parkingLot.getPriceIntervals() != null) {
            List<PriceIntervalDTO> priceIntervalDTOs = parkingLot.getPriceIntervals().stream()
                    .map(this::toPriceIntervalDTO)
                    .collect(Collectors.toList());
            dto.setPriceIntervals(priceIntervalDTOs);
        }

        // Owner reference
        if (parkingLot.getOwner() != null) {
            dto.setOwnerId(parkingLot.getOwner().getId());
        }

        return dto;
    }


    public ParkingLot toEntity(ParkingLotDTO dto) {
        if (dto == null) return null;

        ParkingLot entity = new ParkingLot();

        // Map DTO to entity
        updateEntityFromDTO(dto, entity);

        return entity;
    }

    public void updateEntityFromDTO(ParkingLotDTO dto, ParkingLot entity) {
        if (dto == null || entity == null) return;

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getAddress() != null) entity.setAddress(dto.getAddress());
        if (dto.getGpsCoordinates() != null) entity.setGpsCoordinates(dto.getGpsCoordinates());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getMaxVehicleHeight() != null) entity.setMaxVehicleHeight(dto.getMaxVehicleHeight());
        if (dto.getTotalSpots() != null) entity.setTotalSpots(dto.getTotalSpots());
        if (dto.getEvChargingSpots() != null) entity.setEvChargingSpots(dto.getEvChargingSpots());
        if (dto.getDisabilitySpots() != null) entity.setDisabilitySpots(dto.getDisabilitySpots());
        if (dto.getFamilySpots() != null) entity.setFamilySpots(dto.getFamilySpots());
        if (dto.getTimeBeforeReservation() != null) entity.setTimeBeforeReservation(dto.getTimeBeforeReservation());
        if (dto.getTimeAfterReservation() != null) entity.setTimeAfterReservation(dto.getTimeAfterReservation());
        if (dto.getMinParkingDuration() != null) entity.setMinParkingDuration(dto.getMinParkingDuration());
        if (dto.getMaxParkingDuration() != null) entity.setMaxParkingDuration(dto.getMaxParkingDuration());
        if (dto.getMinParkingDurationUnit() != null) entity.setMinParkingDurationUnit(dto.getMinParkingDurationUnit());
        if (dto.getMaxParkingDurationUnit() != null) entity.setMaxParkingDurationUnit(dto.getMaxParkingDurationUnit());
        if (dto.getFreeTimeMinutes() != null) entity.setFreeTimeMinutes(dto.getFreeTimeMinutes());
        if (dto.getBankAccountName() != null) entity.setBankAccountName(dto.getBankAccountName());
        if (dto.getBankAccountNumber() != null) entity.setBankAccountNumber(dto.getBankAccountNumber());
        if (dto.getMaxExtensionTime() != null) entity.setMaxExtensionTime(dto.getMaxExtensionTime());
        if (dto.getExtensionPricingPercentage() != null) entity.setExtensionPricingPercentage(dto.getExtensionPricingPercentage());
        if (dto.getPreReservationCancelWindow() != null) entity.setPreReservationCancelWindow(dto.getPreReservationCancelWindow());
        if (dto.getPreReservationCancelFee() != null) entity.setPreReservationCancelFee(dto.getPreReservationCancelFee());
        if (dto.getMidReservationCancelWindow() != null) entity.setMidReservationCancelWindow(dto.getMidReservationCancelWindow());
        if (dto.getMidReservationCancelFee() != null) entity.setMidReservationCancelFee(dto.getMidReservationCancelFee());
        if (dto.getSpotsAvailable() != null) entity.setSpotsAvailable(dto.getSpotsAvailable());

        entity.setLighted(dto.isLighted());
        entity.setHasVideoSurveillance(dto.isHasVideoSurveillance());
        entity.setAllowReservations(dto.isAllowReservations());
        entity.setAllowDirectPayment(dto.isAllowDirectPayment());
        entity.setRequireQrCode(dto.isRequireQrCode());
        entity.setHasExistingAvailabilitySystem(dto.isHasExistingAvailabilitySystem());
        entity.setSharedWithNonAppUsers(dto.isSharedWithNonAppUsers());
        entity.setDisplayFees(dto.isDisplayFees());
        entity.setHasTimeLimits(dto.isHasTimeLimits());
        entity.setHasFreeTime(dto.isHasFreeTime());
        entity.setAllowExtensions(dto.isAllowExtensions());
        entity.setAllowCancellations(dto.isAllowCancellations());
        entity.setAllowPreReservationCancellations(dto.isAllowPreReservationCancellations());
        entity.setApplyPreCancelFee(dto.isApplyPreCancelFee());
        entity.setAllowMidReservationCancellations(dto.isAllowMidReservationCancellations());
        entity.setApplyMidCancelFee(dto.isApplyMidCancelFee());

        // Direct enum mappings
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getSize() != null) entity.setSize(dto.getSize());
        if (dto.getAvailability() != null) entity.setAvailability(dto.getAvailability());
        if (dto.getPaymentTiming() != null) entity.setPaymentTiming(dto.getPaymentTiming());
        if (dto.getAccessVerificationMethod() != null) entity.setAccessVerificationMethod(dto.getAccessVerificationMethod());
        if (dto.getTimeBeforeReservationUnit() != null) entity.setTimeBeforeReservationUnit(dto.getTimeBeforeReservationUnit());
        if (dto.getTimeAfterReservationUnit() != null) entity.setTimeAfterReservationUnit(dto.getTimeAfterReservationUnit());
        if (dto.getParkingAvailabilityMethod() != null) entity.setParkingAvailabilityMethod(dto.getParkingAvailabilityMethod());
        if (dto.getPricingType() != null) entity.setPricingType(dto.getPricingType());
        if (dto.getExtensionPricingModel() != null) entity.setExtensionPricingModel(dto.getExtensionPricingModel());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());

        // Direct collection of enums
        if (dto.getTypes() != null && !dto.getTypes().isEmpty()) {
            entity.setTypes(new HashSet<>(dto.getTypes()));
        }

        // Photos (string collection)
        if (dto.getPhotos() != null) {
            entity.setPhotos(new ArrayList<>(dto.getPhotos()));
        }

        // Complex object mappings
        // Custom Hour Intervals
        if (dto.getCustomHourIntervals() != null) {
            List<CustomHourInterval> customHourIntervals = dto.getCustomHourIntervals().stream()
                    .map(this::toCustomHourInterval)
                    .collect(Collectors.toList());
            entity.setCustomHourIntervals(customHourIntervals);
        }

        // Price Intervals
        if (dto.getPriceIntervals() != null) {
            List<PriceInterval> priceIntervals = dto.getPriceIntervals().stream()
                    .map(this::toPriceInterval)
                    .collect(Collectors.toList());
            entity.setPriceIntervals(priceIntervals);
        }
    }

    private CustomHourIntervalDTO toCustomHourIntervalDTO(CustomHourInterval entity) {
        if (entity == null) return null;

        CustomHourIntervalDTO dto = new CustomHourIntervalDTO();
        dto.setId(entity.getId());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDays(entity.getDays());  // Direct enum list assignment

        return dto;
    }

    private CustomHourInterval toCustomHourInterval(CustomHourIntervalDTO dto) {
        if (dto == null) return null;

        CustomHourInterval entity = new CustomHourInterval();
        entity.setId(dto.getId());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDays(dto.getDays());  // Direct enum list assignment

        return entity;
    }

    private PriceIntervalDTO toPriceIntervalDTO(PriceInterval entity) {
        if (entity == null) return null;

        PriceIntervalDTO dto = new PriceIntervalDTO();
        dto.setId(entity.getId());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setPrice(entity.getPrice());
        dto.setDuration(entity.getDuration());
        dto.setDays(entity.getDays());  // Direct enum list assignment

        return dto;
    }

    private PriceInterval toPriceInterval(PriceIntervalDTO dto) {
        if (dto == null) return null;

        PriceInterval entity = new PriceInterval();
        entity.setId(dto.getId());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setPrice(dto.getPrice());
        entity.setDuration(dto.getDuration());
        entity.setDays(dto.getDays());  // Direct enum list assignment

        return entity;
    }
}