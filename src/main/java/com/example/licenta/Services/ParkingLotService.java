package com.example.licenta.Services;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.ParkingLot.ParkingLotStatus;
import com.example.licenta.Enum.ParkingLot.PaymentTiming;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidCredentialsException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ParkingLotMapper;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Repositories.ReservationRepository;
import com.example.licenta.Repositories.UserRepository;
import com.example.licenta.Utils.LocationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ParkingLotService {

    private final ParkingLotRepository parkingLotRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingLotMapper parkingLotMapper;
    private final ImageService imageService;

    private static class PeriodDates {
        OffsetDateTime currentStart, currentEnd, prevStart, prevEnd;
        int numberOfUnits;
        ChronoUnit timeUnit;
        Function<OffsetDateTime, String> labelFormatter;

        PeriodDates(OffsetDateTime cs, OffsetDateTime ce, OffsetDateTime ps, OffsetDateTime pe, int nu, ChronoUnit tu, Function<OffsetDateTime, String> lf) {
            currentStart = cs; currentEnd = ce; prevStart = ps; prevEnd = pe;
            numberOfUnits = nu; timeUnit = tu; labelFormatter = lf;
        }
    }

    private static class TimePerUnit {
        OffsetDateTime start, end;
        String label;
        TimePerUnit(OffsetDateTime s, OffsetDateTime e, String l) { start = s; end = e; label = l; }
    }


    @Autowired
    public ParkingLotService(
            ParkingLotRepository parkingLotRepository,
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            ParkingLotMapper parkingLotMapper,
            ImageService imageService) {
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.parkingLotMapper = parkingLotMapper;
        this.imageService = imageService;
    }

    @Transactional
    public ParkingLot createParkingLot(ParkingLotDTO dto, String ownerId) {
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
    public ParkingLot updateParkingLot(String parkingLotId, ParkingLotDTO dto, String userId) {
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
    public void deleteParkingLot(String parkingLotId, String userId) {
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
    public ParkingLot getParkingLotById(String id) {
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

    private PeriodDates calculatePeriodDates(String periodStr, OffsetDateTime now) {
        OffsetDateTime currentStart, currentEnd, prevStart, prevEnd;
        int numberOfUnits;
        ChronoUnit timeUnit;
        Function<OffsetDateTime, String> labelFormatter;

        currentEnd = now;

        switch (periodStr.toLowerCase()) {
            case "1d":
                currentStart = now.minusDays(1).truncatedTo(ChronoUnit.HOURS);
                prevEnd = currentStart.minusNanos(1);
                prevStart = currentStart.minusDays(1);
                numberOfUnits = 24;
                timeUnit = ChronoUnit.HOURS;
                labelFormatter = dt -> String.format("%02d", dt.getHour());
                break;
            case "14d":
                currentStart = now.minusDays(14).with(LocalTime.MIN);
                prevEnd = currentStart.minusNanos(1);
                prevStart = currentStart.minusDays(14);
                numberOfUnits = 14;
                timeUnit = ChronoUnit.DAYS;
                labelFormatter = dt -> String.valueOf(dt.getDayOfMonth());
                break;
            case "30d":
                currentStart = now.minusDays(30).with(LocalTime.MIN);
                prevEnd = currentStart.minusNanos(1);
                prevStart = currentStart.minusDays(30);
                numberOfUnits = 30;
                timeUnit = ChronoUnit.DAYS;
                labelFormatter = dt -> String.valueOf(dt.getDayOfMonth());
                break;
            case "7d":
            default:
                currentStart = now.minusDays(7).with(LocalTime.MIN);
                prevEnd = currentStart.minusNanos(1);
                prevStart = currentStart.minusDays(7);
                numberOfUnits = 7;
                timeUnit = ChronoUnit.DAYS;
                labelFormatter = dt -> dt.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US);
                break;
        }
        if (currentStart.isAfter(currentEnd) && "1d".equals(periodStr.toLowerCase())) {
            currentStart = currentEnd.minusDays(1);
        }
        return new PeriodDates(currentStart, currentEnd, prevStart, prevEnd, numberOfUnits, timeUnit, labelFormatter);
    }

    private List<TimePerUnit> getTimeUnits(PeriodDates dates) {
        List<TimePerUnit> units = new ArrayList<>();
        OffsetDateTime unitStart = dates.currentStart;
        for (int i = 0; i < dates.numberOfUnits; i++) {
            OffsetDateTime unitEnd = (dates.timeUnit == ChronoUnit.HOURS) ?
                    unitStart.plusHours(1).minusNanos(1) :
                    unitStart.with(LocalTime.MAX);
            if (unitEnd.isAfter(dates.currentEnd)) unitEnd = dates.currentEnd;

            units.add(new TimePerUnit(unitStart, unitEnd, dates.labelFormatter.apply(unitStart)));
            if (unitStart.isAfter(dates.currentEnd) || unitStart.equals(dates.currentEnd) || unitEnd.equals(dates.currentEnd)) break;
            unitStart = (dates.timeUnit == ChronoUnit.HOURS) ? unitStart.plusHours(1) : unitStart.plusDays(1).with(LocalTime.MIN);
        }
        return units;
    }

    private Double calculateGrowth(double current, double previous) {
        if (previous == 0) return (current == 0) ? 0.0 : 100.0;
        return ((current - previous) / previous) * 100.0;
    }
    private Double calculateGrowth(long currentL, long previousL) {
        double current = (double) currentL;
        double previous = (double) previousL;
        if (previous == 0) return (current == 0) ? 0.0 : 100.0;
        return ((current - previous) / previous) * 100.0;
    }


    @Transactional(readOnly = true)
    public ParkingLotAnalyticsDTO getParkingLotAnalytics(String parkingLotId, String periodString) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found: " + parkingLotId));

        OffsetDateTime now = OffsetDateTime.now();
        PeriodDates dates = calculatePeriodDates(periodString, now);
        List<TimePerUnit> timeUnits = getTimeUnits(dates);

        List<Reservation> paidReservations = reservationRepository.findPaidReservationsForLotInDateRange(
                parkingLotId, dates.prevStart, dates.currentEnd); // Fetch for both periods
        List<Reservation> activePayForUsage = reservationRepository.findActivePayForUsageReservationsForLot(
                parkingLotId, dates.currentEnd);

        // --- Period Metrics & Chart Data ---
        double totalRevenueCurrent = 0.0;
        long totalReservationsCurrent = 0L;
        List<Double> dailyOccupancyRatesCurrent = new ArrayList<>();

        List<ChartDataPointDTO<String, Double>> revenueChart = new ArrayList<>();
        List<ChartDataPointDTO<String, Long>> reservationChart = new ArrayList<>();
        List<ChartDataPointDTO<String, Double>> occupancyChart = new ArrayList<>();

        for (TimePerUnit unit : timeUnits) {
            double unitRevenue = 0.0;
            long unitReservations = 0L;
            long unitActiveReservationInstances = 0;

            for (Reservation r : paidReservations) {
                if (r.getEndTime().isAfter(unit.start.minusNanos(1)) && r.getEndTime().isBefore(unit.end.plusNanos(1))) {
                    unitRevenue += r.getTotalAmount();
                    unitReservations++;
                }
                if (r.getStartTime().isBefore(unit.end) && r.getEndTime().isAfter(unit.start)) {
                    unitActiveReservationInstances++;
                }
            }
            for (Reservation r : activePayForUsage) {
                if (r.getStartTime().isBefore(unit.end)) {
                    unitActiveReservationInstances++;
                }
            }
            revenueChart.add(ChartDataPointDTO.<String, Double>builder().label(unit.label).value(unitRevenue).build());
            reservationChart.add(ChartDataPointDTO.<String, Long>builder().label(unit.label).value(unitReservations).build());

            double unitOccupancy = (parkingLot.getTotalSpots() > 0) ?
                    Math.min(((double) unitActiveReservationInstances / parkingLot.getTotalSpots()) * 100.0, 100.0) : 0.0;
            occupancyChart.add(ChartDataPointDTO.<String, Double>builder().label(unit.label).value(unitOccupancy).build());
            if(unit.start.isAfter(dates.currentStart.minusNanos(1)) && unit.end.isBefore(dates.currentEnd.plusNanos(1))) {
                dailyOccupancyRatesCurrent.add(unitOccupancy);
            }
        }

        for (Reservation r : paidReservations) {
            if (r.getEndTime().isAfter(dates.currentStart.minusNanos(1)) && r.getEndTime().isBefore(dates.currentEnd.plusNanos(1))) {
                totalRevenueCurrent += r.getTotalAmount();
                totalReservationsCurrent++;
            }
        }
        double averageOccupancyCurrent = dailyOccupancyRatesCurrent.stream().mapToDouble(d->d).average().orElse(0.0);

        // Previous Period
        double totalRevenuePrevious = 0.0;
        long totalReservationsPrevious = 0L;
        for (Reservation r : paidReservations) {
            if (r.getEndTime().isAfter(dates.prevStart.minusNanos(1)) && r.getEndTime().isBefore(dates.prevEnd.plusNanos(1))) {
                totalRevenuePrevious += r.getTotalAmount();
                totalReservationsPrevious++;
            }
        }

        // Cancellation Rate
        Long cancelledInCurrentPeriod = reservationRepository.countCancelledReservationsForLotInDateRange(parkingLotId, dates.currentStart, dates.currentEnd);
        Long totalAttemptedInCurrentPeriod = reservationRepository.countTotalAttemptedReservationsForLotInDateRange(parkingLotId, dates.currentStart, dates.currentEnd);
        double cancellationRate = (totalAttemptedInCurrentPeriod > 0) ?
                ((double) cancelledInCurrentPeriod / totalAttemptedInCurrentPeriod) * 100.0 : 0.0;

        PeriodAnalyticsDTO periodAnalytics = PeriodAnalyticsDTO.builder()
                .revenue(totalRevenueCurrent)
                .reservations(totalReservationsCurrent)
                .averageRevenue((totalReservationsCurrent > 0) ? totalRevenueCurrent / totalReservationsCurrent : 0.0)
                .occupancyRate(averageOccupancyCurrent)
                .cancellationRate(cancellationRate)
                .revenueGrowth(calculateGrowth(totalRevenueCurrent, totalRevenuePrevious))
                .reservationGrowth(calculateGrowth(totalReservationsCurrent, totalReservationsPrevious))
                .build();

        // --- Peak Hours Data (Example: for the last 24 hours if period is '1d', or aggregated for longer) ---
        List<PeakHourDataDTO> peakHours = new ArrayList<>();
        // For simplicity, let's calculate peak hours based on the 'currentStart' to 'currentEnd' of the selected period
        // For '1d', this will naturally be hour by hour. For longer periods, this sums up e.g. all Mondays at 9 AM.
        Map<Integer, Long> reservationsByHour = new HashMap<>();
        Map<Integer, Double> revenueByHour = new HashMap<>();
        Map<Integer, Long> activeInstancesByHour = new HashMap<>(); // For hourly occupancy

        List<Reservation> relevantReservationsForPeakHours = reservationRepository.findPaidReservationsForLotInDateRange(parkingLotId, dates.currentStart, dates.currentEnd);
        relevantReservationsForPeakHours.addAll(reservationRepository.findActivePayForUsageReservationsForLot(parkingLotId, dates.currentEnd));


        for (Reservation r : relevantReservationsForPeakHours) {
            OffsetDateTime resTime = r.getEndTime() != null ? r.getEndTime() : r.getStartTime(); // Use endTime for paid, startTime for active PFU
            if (resTime.isAfter(dates.currentStart.minusNanos(1)) && resTime.isBefore(dates.currentEnd.plusNanos(1))) {
                int hour = resTime.getHour();
                reservationsByHour.merge(hour, 1L, Long::sum);
                if (r.getTotalAmount() != null) { // PFU might not have totalAmount yet if still active
                    revenueByHour.merge(hour, r.getTotalAmount(), Double::sum);
                }
            }
            // For hourly occupancy contribution: iterate through each hour the reservation was active in the current period
            OffsetDateTime rStart = r.getStartTime();
            OffsetDateTime rEnd = r.getEndTime() != null ? r.getEndTime() : dates.currentEnd; // Cap PFU at current time for this calc

            OffsetDateTime currentHourSlot = rStart.isBefore(dates.currentStart) ? dates.currentStart.truncatedTo(ChronoUnit.HOURS) : rStart.truncatedTo(ChronoUnit.HOURS);
            while(currentHourSlot.isBefore(rEnd) && currentHourSlot.isBefore(dates.currentEnd)){
                if(currentHourSlot.isAfter(dates.currentStart.minusNanos(1))){ // Only count hours within the current display period
                    activeInstancesByHour.merge(currentHourSlot.getHour(), 1L, Long::sum);
                }
                currentHourSlot = currentHourSlot.plusHours(1);
            }
        }

        for (int i = 0; i < 24; i++) {
            long hourlyReservations = reservationsByHour.getOrDefault(i, 0L);
            double hourlyRevenue = revenueByHour.getOrDefault(i, 0.0);
            long hourlyActiveInstances = activeInstancesByHour.getOrDefault(i,0L);
            double hourlyOccupancy = (parkingLot.getTotalSpots() > 0) ?
                    Math.min(((double) hourlyActiveInstances / parkingLot.getTotalSpots()) * 100.0, 100.0) : 0.0;

            peakHours.add(PeakHourDataDTO.builder()
                    .hour(i)
                    .reservations(hourlyReservations)
                    .revenue(hourlyRevenue)
                    .occupancyRate(hourlyOccupancy)
                    .build());
        }


        Double avgRating = reservationRepository.getAverageRatingForParkingLot(parkingLotId);
        if (avgRating == null) avgRating = 0.0; // Handle case with no reviews

        long totalDistinctUsers = reservationRepository.countDistinctUsersForLot(parkingLotId, dates.currentEnd);
        long usersWithMultipleReservations = reservationRepository.countUsersWithMultipleReservationsForLot(parkingLotId, dates.currentEnd).size();
        double repeatCustomerRate = (totalDistinctUsers > 0) ? ((double) usersWithMultipleReservations / totalDistinctUsers) * 100.0 : 0.0;

        // Popular Reservation Types
        List<Object[]> typeCountsRaw = reservationRepository.countReservationsByTypeForLot(parkingLotId, dates.currentStart, dates.currentEnd);
        long totalTypedReservations = typeCountsRaw.stream().mapToLong(obj -> (Long) obj[1]).sum();
        List<PopularReservationTypeDTO> popularTypes = typeCountsRaw.stream().map(obj -> {
            ReservationType type = (ReservationType) obj[0];
            Long count = (Long) obj[1];
            return PopularReservationTypeDTO.builder()
                    .type(type.name())
                    .count(count)
                    .percentage((totalTypedReservations > 0) ? ((double) count / totalTypedReservations) * 100.0 : 0.0)
                    .build();
        }).collect(Collectors.toList());

        // Average Session Duration (Needs more thought - how is session defined? Using reservation duration for now)
        // Taking average duration of PAID reservations completed in the current period
        double avgSessionDurationMinutes = paidReservations.stream()
                .filter(r -> r.getEndTime() != null && r.getStartTime() != null && r.getEndTime().isAfter(dates.currentStart.minusNanos(1)) && r.getEndTime().isBefore(dates.currentEnd.plusNanos(1)))
                .mapToLong(r -> Duration.between(r.getStartTime(), r.getEndTime()).toMinutes())
                .average()
                .orElse(0.0);


        CustomerInsightsDTO customerInsights = CustomerInsightsDTO.builder()
                .averageSessionDuration(avgSessionDurationMinutes)
                .repeatCustomerRate(repeatCustomerRate)
                .averageRating(avgRating)
                .popularReservationTypes(popularTypes)
                .build();

        return ParkingLotAnalyticsDTO.builder()
                .periodAnalytics(periodAnalytics)
                .revenueChartData(revenueChart)
                .reservationChartData(reservationChart)
                .occupancyChartData(occupancyChart)
                .peakHoursData(peakHours)
                .customerInsights(customerInsights)
                .build();
    }
}