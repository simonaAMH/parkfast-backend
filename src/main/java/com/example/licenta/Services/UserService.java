package com.example.licenta.Services;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.User.Role;
import com.example.licenta.Exceptions.*;
import com.example.licenta.Models.*;
import com.example.licenta.Repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final UserVehiclePlateRepository vehiclePlateRepository;
    private final UserPaymentMethodRepository paymentMethodRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ImageService imageService;

    @Autowired
    public UserService(UserRepository userRepository,
                       UserVehiclePlateRepository vehiclePlateRepository,
                       UserPaymentMethodRepository paymentMethodRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       ImageService imageService,
                       ParkingLotRepository parkingLotRepository,
                       WithdrawalRepository withdrawalRepository) {
        this.userRepository = userRepository;
        this.vehiclePlateRepository = vehiclePlateRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.imageService = imageService;
        this.parkingLotRepository = parkingLotRepository;
        this.withdrawalRepository = withdrawalRepository;
    }

    @Transactional
    public User registerUser(UserRegistrationDTO userDTO) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new UserExistsException("Email already in use");
        }

        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new UserExistsException("Username already in use");
        }

        Role role = userDTO.getRole() != null ? userDTO.getRole() : Role.USER;

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRole(role);
        user.setEmailVerified(false);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if (userDTO.getProfileImage() != null && !userDTO.getProfileImage().isEmpty()) {
            try {
                String imageUrl = imageService.saveImage(userDTO.getProfileImage());
                user.setProfileImage(imageUrl);
            } catch (IOException e) {
                System.err.println("Failed to save profile image: " + e.getMessage());
                user.setProfileImage(null);
            }
        } else {
            user.setProfileImage(null);
        }

        String token = generateToken();
        user.setEmailVerificationToken(token);
        user.setTokenExpiry(OffsetDateTime.now().plusDays(1));

        User savedUser = userRepository.save(user);

        emailService.sendVerificationEmail(savedUser.getEmail(), token);
        emailService.sendAccountCreationConfirmationEmail(savedUser.getEmail(), savedUser.getUsername());

        return savedUser;
    }

    public Optional<User> loginUser(String emailOrUsername, String password) {
        Optional<User> userOptional = userRepository.findByEmail(emailOrUsername)
                .or(() -> userRepository.findByUsername(emailOrUsername));

        if (userOptional.isPresent() && passwordEncoder.matches(password, userOptional.get().getPassword())) {
            return userOptional;
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired verification token"));

        if (user.isEmailVerified()) {
            return;
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with this email"));

        String token = generateToken();
        user.setPasswordResetToken(token);
        user.setTokenExpiry(OffsetDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public User resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new AuthenticationException("Invalid password reset token"));

        if (user.getTokenExpiry().isBefore(OffsetDateTime.now())) {
            throw new AuthenticationException("Token has expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setTokenExpiry(null);
        return userRepository.save(user);
    }

    @Transactional
    public void initiateAccountDeletion(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with this email"));

        String token = generateToken();
        user.setAccountDeletionToken(token);
        user.setTokenExpiry(OffsetDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendAccountDeletionEmail(user.getEmail(), token);
    }

    @Transactional
    public void deleteAccount(String token) {
        User user = userRepository.findByAccountDeletionToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired deletion token"));

        userRepository.delete(user);
    }

    @Transactional
    public User updateUser(String userId, UserUpdateDTO updateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (updateDto.getLoyaltyPoints() != null) {
            user.setLoyaltyPoints(updateDto.getLoyaltyPoints());
        }

        if (updateDto.getUsername() != null && !updateDto.getUsername().isEmpty()) {
            if (!user.getUsername().equals(updateDto.getUsername()) &&
                    userRepository.existsByUsername(updateDto.getUsername())) {
                throw new UserExistsException("Username already in use");
            }
            user.setUsername(updateDto.getUsername());
        }

        if (updateDto.getEmail() != null && !updateDto.getEmail().isEmpty()) {
            boolean emailChanged = !updateDto.getEmail().equalsIgnoreCase(user.getEmail());
            if (emailChanged && userRepository.existsByEmail(updateDto.getEmail())) {
                throw new UserExistsException("Email already in use");
            }
            user.setEmail(updateDto.getEmail());
            if (emailChanged) {
                user.setEmailVerified(false);
                String token = generateToken();
                user.setEmailVerificationToken(token);
                user.setTokenExpiry(OffsetDateTime.now().plusDays(1));
                emailService.sendVerificationEmail(user.getEmail(), token);
            }
        }

        if (updateDto.getProfileImage() == null || updateDto.getProfileImage().isEmpty()) {
            user.setProfileImage(null);
        } else {
            try {
                String imageUrl = imageService.saveImage(updateDto.getProfileImage());
                user.setProfileImage(imageUrl);
            } catch (IOException e) {
                System.err.println("Failed to save profile image during update: " + e.getMessage());
            }
        }

        if (updateDto.getCurrentPassword() != null && !updateDto.getCurrentPassword().isEmpty() &&
                updateDto.getNewPassword() != null && !updateDto.getNewPassword().isEmpty()) {
            if (!passwordEncoder.matches(updateDto.getCurrentPassword(), user.getPassword())) {
                throw new InvalidCredentialsException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(updateDto.getNewPassword()));
        }

        if (updateDto.getRole() != null) {
            user.setRole(updateDto.getRole());
        }

        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.isEmailVerified()) {
            return;
        }

        String token = generateToken();
        user.setEmailVerificationToken(token);
        user.setTokenExpiry(OffsetDateTime.now().plusDays(1));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    @Transactional
    public UserVehiclePlate addVehiclePlate(String userId, UserVehiclePlateDTO plateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String normalizedPlateNumber = plateDto.getPlateNumber().toUpperCase().replaceAll("\\s+", "");
        if (vehiclePlateRepository.existsByUserIdAndPlateNumber(userId, normalizedPlateNumber)) {
            throw new InvalidDataException("This vehicle plate is already registered for this user.");
        }

        UserVehiclePlate plate = new UserVehiclePlate();
        plate.setUser(user);
        plate.setPlateNumber(normalizedPlateNumber);

        return vehiclePlateRepository.save(plate);
    }

    @Transactional
    public void deleteVehiclePlate(String userId, String plateId) {
        UserVehiclePlate plate = vehiclePlateRepository.findByUserIdAndId(userId, plateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle plate not found with ID: " + plateId + " for user: " + userId));

        vehiclePlateRepository.delete(plate);
    }

    @Transactional(readOnly = true)
    public List<UserVehiclePlate> getVehiclePlates(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        return vehiclePlateRepository.findByUserId(userId);
    }

    @Transactional
    public UserPaymentMethod addPaymentMethod(String userId, UserPaymentMethodDTO methodDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        int currentYear = OffsetDateTime.now().getYear();
        if (methodDto.getExpiryYear() < currentYear ||
                (methodDto.getExpiryYear() == currentYear && methodDto.getExpiryMonth() < OffsetDateTime.now().getMonthValue())) {
            throw new InvalidDataException("Payment card has expired.");
        }

        UserPaymentMethod method = new UserPaymentMethod();
        method.setUser(user);
        method.setCardNumber(methodDto.getCardNumber());
        method.setExpiryMonth(methodDto.getExpiryMonth());
        method.setExpiryYear(methodDto.getExpiryYear());

        return paymentMethodRepository.save(method);
    }

    @Transactional
    public void deletePaymentMethod(String userId, String methodId) {
        UserPaymentMethod method = paymentMethodRepository.findByUserIdAndId(userId, methodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment method not found with ID: " + methodId + " for user: " + userId));

        paymentMethodRepository.delete(method);
    }

    @Transactional(readOnly = true)
    public List<UserPaymentMethod> getPaymentMethods(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        return paymentMethodRepository.findByUserId(userId);
    }

    @Transactional
    public WithdrawalResponseDTO requestWithdrawal(String userId, WithdrawalRequestDTO withdrawalRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        ParkingLot parkingLot = parkingLotRepository.findById(withdrawalRequest.getParkingLotId())
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found: " + withdrawalRequest.getParkingLotId()));

        // Verify user owns the parking lot
        if (!parkingLot.getOwner().getId().equals(userId)) {
            throw new InvalidDataException("You are not the owner of this parking lot");
        }

        // Check if parking lot has bank account details
        if (parkingLot.getBankAccountName() == null || parkingLot.getBankAccountName().trim().isEmpty() ||
                parkingLot.getBankAccountNumber() == null || parkingLot.getBankAccountNumber().trim().isEmpty()) {
            throw new InvalidDataException("Bank account details are not configured for this parking lot. Please update your parking lot settings.");
        }

        // Check if user has pending earnings
        Double pendingEarnings = user.getPendingEarnings();
        if (pendingEarnings == null || pendingEarnings <= 0) {
            throw new InvalidDataException("No pending earnings available for withdrawal");
        }

        // Check if there's already a pending/processing withdrawal for this parking lot
        boolean hasPendingWithdrawal = withdrawalRepository.existsByUserIdAndParkingLotIdAndStatusIn(
                userId,
                parkingLot.getId(),
                List.of(Withdrawal.WithdrawalStatus.PENDING, Withdrawal.WithdrawalStatus.PROCESSING)
        );

        if (hasPendingWithdrawal) {
            throw new InvalidDataException("There is already a pending withdrawal request for this parking lot");
        }

        // Create withdrawal record
        Withdrawal withdrawal = Withdrawal.builder()
                .user(user)
                .parkingLot(parkingLot)
                .amount(pendingEarnings)
                .bankAccountName(parkingLot.getBankAccountName())
                .bankAccountNumber(parkingLot.getBankAccountNumber())
                .status(Withdrawal.WithdrawalStatus.PENDING)
                .build();

        Withdrawal savedWithdrawal = withdrawalRepository.save(withdrawal);

        // Process the withdrawal immediately (you can also queue this for background processing)
        try {
            processWithdrawalPayment(savedWithdrawal);
        } catch (Exception e) {
            // If processing fails, mark as failed
            savedWithdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            savedWithdrawal.setFailureReason("Payment processing failed: " + e.getMessage());
            savedWithdrawal.setProcessedAt(OffsetDateTime.now());
            withdrawalRepository.save(savedWithdrawal);

            throw new InvalidDataException("Withdrawal processing failed: " + e.getMessage());
        }

        return convertToWithdrawalResponseDTO(savedWithdrawal);
    }

    @Transactional
    protected void processWithdrawalPayment(Withdrawal withdrawal) throws InterruptedException {
        try {
            // Update status to processing
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.PROCESSING);
            withdrawalRepository.save(withdrawal);

            // For testing purposes, we'll simulate a successful transfer
            // In production, you would integrate with your actual payment processor

            // Simulate processing delay (remove in production)
            Thread.sleep(1000);

            // Mark as completed
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.COMPLETED);
            withdrawal.setProcessedAt(OffsetDateTime.now());
            withdrawal.setStripePayoutId("test_payout_" + System.currentTimeMillis()); // Simulated payout ID
            withdrawalRepository.save(withdrawal);

            // Update user's earnings
            User user = withdrawal.getUser();
            Double withdrawnAmount = withdrawal.getAmount();

            user.setPendingEarnings(0.0); // All pending earnings withdrawn

            Double currentPaidEarnings = Optional.ofNullable(user.getPaidEarnings()).orElse(0.0);
            user.setPaidEarnings(currentPaidEarnings + withdrawnAmount);

            userRepository.save(user);

            emailService.sendWithdrawalConfirmationEmail(
                    user.getEmail(),
                    withdrawal.getId(),
                    withdrawnAmount,
                    withdrawal.getBankAccountNumber(),
                    withdrawal.getParkingLot().getName()
            );

        } catch (Exception e) {
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(e.getMessage());
            withdrawal.setProcessedAt(OffsetDateTime.now());
            withdrawalRepository.save(withdrawal);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalResponseDTO> getUserWithdrawals(String userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        Page<Withdrawal> withdrawalPage = withdrawalRepository.findByUserIdOrderByRequestedAtDesc(userId, pageable);

        return withdrawalPage.map(this::convertToWithdrawalResponseDTO);
    }

    @Transactional(readOnly = true)
    public WithdrawalSummaryDTO getWithdrawalSummary(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Get recent withdrawals (last 5)
        List<Withdrawal> recentWithdrawals = withdrawalRepository
                .findByUserIdAndRequestedAtAfterOrderByRequestedAtDesc(
                        userId,
                        OffsetDateTime.now().minusMonths(3)
                )
                .stream()
                .limit(5)
                .toList();

        List<ParkingLot> userParkingLots = parkingLotRepository.findByOwner(user);

        List<ParkingLotWithdrawalInfoDTO> availableParkingLots = userParkingLots.stream()
                .map(lot -> {
                    boolean canWithdraw = lot.getBankAccountName() != null &&
                            lot.getBankAccountNumber() != null &&
                            user.getPendingEarnings() != null &&
                            user.getPendingEarnings() > 0;

                    String blockReason = null;
                    if (lot.getBankAccountName() == null || lot.getBankAccountNumber() == null) {
                        blockReason = "Bank account details not configured";
                    } else if (user.getPendingEarnings() == null || user.getPendingEarnings() <= 0) {
                        blockReason = "No pending earnings available";
                    }

                    return ParkingLotWithdrawalInfoDTO.builder()
                            .id(lot.getId())
                            .name(lot.getName())
                            .address(lot.getAddress())
                            .bankAccountName(lot.getBankAccountName())
                            .bankAccountNumber(lot.getBankAccountNumber())
                            .pendingEarnings(user.getPendingEarnings())
                            .canWithdraw(canWithdraw)
                            .withdrawalBlockReason(blockReason)
                            .build();
                })
                .toList();

        return WithdrawalSummaryDTO.builder()
                .totalPendingEarnings(user.getPendingEarnings())
                .totalPaidEarnings(user.getPaidEarnings())
                .totalWithdrawals((int) withdrawalRepository.countByUserId(userId))
                .recentWithdrawals(recentWithdrawals.stream()
                        .map(this::convertToWithdrawalResponseDTO)
                        .toList())
                .availableParkingLots(availableParkingLots)
                .build();
    }

    @Transactional(readOnly = true)
    public WithdrawalResponseDTO getWithdrawalById(String userId, String withdrawalId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found: " + withdrawalId));

        if (!withdrawal.getUser().getId().equals(userId)) {
            throw new InvalidDataException("Withdrawal does not belong to this user");
        }

        return convertToWithdrawalResponseDTO(withdrawal);
    }

    private WithdrawalResponseDTO convertToWithdrawalResponseDTO(Withdrawal withdrawal) {
        return WithdrawalResponseDTO.builder()
                .id(withdrawal.getId())
                .userId(withdrawal.getUser().getId())
                .parkingLotId(withdrawal.getParkingLot().getId())
                .parkingLotName(withdrawal.getParkingLot().getName())
                .amount(withdrawal.getAmount())
                .bankAccountName(withdrawal.getBankAccountName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .status(withdrawal.getStatus())
                .failureReason(withdrawal.getFailureReason())
                .requestedAt(withdrawal.getRequestedAt())
                .processedAt(withdrawal.getProcessedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}