package com.example.licenta.Services;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.User.Role;
import com.example.licenta.Exceptions.*;
import com.example.licenta.Models.*;
import com.example.licenta.Repositories.*;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;
import com.stripe.model.Transfer;
import com.stripe.param.AccountUpdateParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final UserVehiclePlateRepository vehiclePlateRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ImageService imageService;
    private final StripeService stripeService;

    @Autowired
    public UserService(UserRepository userRepository,
                       UserVehiclePlateRepository vehiclePlateRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       ImageService imageService,
                       ParkingLotRepository parkingLotRepository,
                       WithdrawalRepository withdrawalRepository,
                       StripeService stripeService) {
        this.userRepository = userRepository;
        this.vehiclePlateRepository = vehiclePlateRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.imageService = imageService;
        this.parkingLotRepository = parkingLotRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.stripeService = stripeService;
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
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (passwordEncoder.matches(password, user.getPassword())) {
                if (!user.isEmailVerified()) {
                    throw new InvalidDataException("Please verify your email address before logging in");
                }
                return userOptional;
            }
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

        if (updateDto.getBankAccountName() != null) {
            user.setBankAccountName(updateDto.getBankAccountName());
        }

        if (updateDto.getBankAccountNumber() != null) {
            user.setBankAccountNumber(updateDto.getBankAccountNumber());
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
    public WithdrawalResponseDTO requestWithdrawal(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Verify user owns at least one parking lot
        List<ParkingLot> userParkingLots = parkingLotRepository.findByOwner(user);
        if (userParkingLots.isEmpty()) {
            throw new InvalidDataException("You don't own any parking lots.");
        }

        if (user.getBankAccountName() == null || user.getBankAccountName().trim().isEmpty() ||
                user.getBankAccountNumber() == null || user.getBankAccountNumber().trim().isEmpty()) {
            throw new InvalidDataException("Bank account details are not configured for your account. Please update your profile settings.");
        }

        Double pendingEarnings = user.getPendingEarnings();
        if (pendingEarnings == null || pendingEarnings <= 0) {
            throw new InvalidDataException("No pending earnings available for withdrawal.");
        }

        // Check for any pending withdrawal for this user (across all parking lots)
        boolean hasPendingWithdrawal = withdrawalRepository.existsByUserIdAndStatusIn(
                userId,
                List.of(Withdrawal.WithdrawalStatus.PENDING, Withdrawal.WithdrawalStatus.PROCESSING)
        );

        if (hasPendingWithdrawal) {
            throw new InvalidDataException("You already have a pending withdrawal request. Please wait for it to complete before requesting another withdrawal.");
        }

        String withdrawalId = UUID.randomUUID().toString();

        // Create withdrawal record
        Withdrawal withdrawal = Withdrawal.builder()
                .id(withdrawalId)
                .user(user)
                .amount(pendingEarnings)
                .bankAccountName(user.getBankAccountName())
                .bankAccountNumber(user.getBankAccountNumber())
                .status(Withdrawal.WithdrawalStatus.PENDING)
                .requestedAt(OffsetDateTime.now())
                .build();

        // Save the withdrawal
        withdrawal = withdrawalRepository.save(withdrawal);

        // Process the payment in the same transaction
        return processWithdrawalPaymentInternal(withdrawal, user, pendingEarnings);
    }

    private WithdrawalResponseDTO processWithdrawalPaymentInternal(Withdrawal withdrawal, User user, Double amountToWithdraw) {
        // Update status to PROCESSING
        withdrawal.setStatus(Withdrawal.WithdrawalStatus.PROCESSING);
        withdrawal = withdrawalRepository.save(withdrawal);

        try {
            String connectedAccountId = user.getStripeConnectedAccountId();
            Account stripeAccount;

            if (connectedAccountId == null || connectedAccountId.isEmpty()) {
                System.out.println("UserService: No Stripe Connected Account found for user: " + user.getId() + ". Creating one.");
                stripeAccount = stripeService.createCustomConnectedAccount(user.getEmail(), "RO");
                connectedAccountId = stripeAccount.getId();
                user.setStripeConnectedAccountId(connectedAccountId);
                System.out.println("UserService: Created Stripe Connected Account ID: " + connectedAccountId + " for user: " + user.getId());

                // Update account details
                AccountUpdateParams.Individual.Dob dob = AccountUpdateParams.Individual.Dob.builder()
                        .setDay(1L)
                        .setMonth(1L)
                        .setYear(1990L)
                        .build();
                AccountUpdateParams.Individual.Address address = AccountUpdateParams.Individual.Address.builder()
                        .setLine1("Default Address Line 1")
                        .setCity("Default City")
                        .setPostalCode("000000")
                        .setCountry("RO")
                        .build();

                AccountUpdateParams.BusinessProfile businessProfile =
                        AccountUpdateParams.BusinessProfile.builder()
                                .setUrl("https://parkfast.it")
                                .build();

                stripeAccount = stripeService.updateConnectedAccountIndividualDetails(
                        connectedAccountId,
                        Optional.ofNullable(user.getUsername()).orElse(user.getUsername()),
                        "LastName",
                        user.getEmail(),
                        dob,
                        address,
                        Optional.ofNullable(user.getPhoneNumber()).orElse("+40700000000"),
                        null,
                        businessProfile
                );
                System.out.println("UserService: Updated individual details for Connected Account: " + connectedAccountId);

                stripeAccount = stripeService.acceptTosForConnectedAccount(
                        connectedAccountId,
                        "127.0.0.1",
                        Instant.now().getEpochSecond()
                );
                System.out.println("UserService: Updated ToS acceptance for Connected Account: " + connectedAccountId);

                System.out.println("UserService: Adding IBAN " + user.getBankAccountNumber() + " for " + user.getBankAccountName() + " to Connected Account " + connectedAccountId);
                BankAccount externalBankAccount = stripeService.addExternalBankAccountToConnectedAccount(
                        connectedAccountId,
                        user.getBankAccountName(),
                        user.getBankAccountNumber(),
                        true
                );
                System.out.println("UserService: Added bank account " + externalBankAccount.getId() + " to Connected Account: " + connectedAccountId);

                stripeAccount = stripeService.requestCapabilitiesForConnectedAccount(connectedAccountId, List.of("transfers"));
                System.out.println("UserService: Ensured 'transfers' capability for Connected Account: " + connectedAccountId);
            } else {
                System.out.println("UserService: Found existing Stripe Connected Account ID: " + connectedAccountId + " for user: " + user.getId());
                stripeAccount = Account.retrieve(connectedAccountId);
            }

            if (!stripeAccount.getPayoutsEnabled()) {
                String requirementsDetails = stripeAccount.getRequirements() != null ? stripeAccount.getRequirements().toString() : "N/A";
                System.err.println("UserService WARNING: Stripe Connected Account " + connectedAccountId + " is not enabled for payouts. PayoutsEnabled: " + stripeAccount.getPayoutsEnabled() + ". Requirements: " + requirementsDetails);
                if (stripeAccount.getRequirements() != null && !stripeAccount.getRequirements().getCurrentlyDue().isEmpty()){
                    String missingFields = String.join(", ", stripeAccount.getRequirements().getCurrentlyDue());
                    throw new PaymentProcessingException("Stripe Connected Account requires more information to enable payouts: " + missingFields + ". Please update your account details.");
                } else if (!stripeAccount.getPayoutsEnabled()) {
                    throw new PaymentProcessingException("Stripe Connected Account payouts are not enabled. Please check account status and requirements in Stripe dashboard or contact support. Details: " + requirementsDetails);
                }
            }

            if (stripeAccount.getExternalAccounts() == null || stripeAccount.getExternalAccounts().getData().isEmpty()) {
                System.err.println("UserService ERROR: No external bank account found on Connected Account: " + connectedAccountId + ". Cannot make transfer.");
                throw new PaymentProcessingException("Stripe Connect account setup incomplete: Missing bank account on Stripe Connected Account. Please add a bank account.");
            }

            long amountInSmallestUnit = Math.round(amountToWithdraw * 100);
            Map<String, String> transferMetadata = Map.of(
                    "internal_withdrawal_id", withdrawal.getId(),
                    "platform_user_id", user.getId(),
                    "withdrawal_type", "ALL_PARKING_LOTS",
                    "initiated_by_user_login", "simonaAMH"
            );
            String transferGroup = "WITHDRAWAL-" + withdrawal.getId();

            System.out.println("UserService: Attempting to transfer " + amountInSmallestUnit + " RON to Connected Account: " + connectedAccountId + " for withdrawal: " + withdrawal.getId());
            Transfer stripeTransfer = stripeService.createTransferToDestination(
                    amountInSmallestUnit,
                    "RON",
                    connectedAccountId,
                    transferMetadata,
                    transferGroup
            );

            // SUCCESS: Update withdrawal status to COMPLETED
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.COMPLETED);
            withdrawal.setStripePayoutId(stripeTransfer.getId());
            withdrawal.setProcessedAt(OffsetDateTime.now());

            // Update user earnings
            Double currentPending = Optional.ofNullable(user.getPendingEarnings()).orElse(0.0);
            Double currentPaid = Optional.ofNullable(user.getPaidEarnings()).orElse(0.0);
            user.setPendingEarnings(Math.max(0, currentPending - amountToWithdraw));
            user.setPaidEarnings(currentPaid + amountToWithdraw);

            // Save both entities
            withdrawal = withdrawalRepository.save(withdrawal);
            userRepository.save(user);

            // Send confirmation email
            List<ParkingLot> userParkingLots = parkingLotRepository.findByOwner(user);
            String parkingLotNames = userParkingLots.stream()
                    .map(ParkingLot::getName)
                    .collect(Collectors.joining(", "));

            emailService.sendWithdrawalConfirmationEmail(
                    user.getEmail(),
                    withdrawal.getId(),
                    amountToWithdraw,
                    user.getBankAccountNumber(),
                    "All Parking Lots (" + parkingLotNames + ")"
            );

            return convertToWithdrawalResponseDTO(withdrawal);

        } catch (StripeException e) {
            String errorMessage = getStripeErrorMessage(e);
            System.err.println("UserService StripeException during Connect withdrawal for ID " + withdrawal.getId() + ": " + errorMessage);

            // Update withdrawal to failed in same transaction
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason("Stripe Error: " + errorMessage);
            withdrawalRepository.save(withdrawal);

            throw new PaymentProcessingException(errorMessage);

        } catch (PaymentProcessingException ppe) {
            System.err.println("UserService PaymentProcessingException during Connect withdrawal for ID " + withdrawal.getId() + ": " + ppe.getMessage());

            // Update withdrawal to failed in same transaction
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(ppe.getMessage());
            withdrawalRepository.save(withdrawal);

            throw ppe;

        } catch (Exception e) {
            System.err.println("UserService General Exception during Connect withdrawal for ID " + withdrawal.getId() + ": " + e.getMessage());

            // Update withdrawal to failed in same transaction
            withdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason("General Error: " + e.getMessage());
            withdrawalRepository.save(withdrawal);

            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new PaymentProcessingException("Withdrawal processing failed due to an unexpected error: " + e.getMessage());
        }
    }

    @Transactional
    public WithdrawalResponseDTO processWithdrawalPayment(String userId, String withdrawalId, String parkingLotId, Double amountToWithdraw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Always get fresh withdrawal from database
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found: " + withdrawalId));

        // Update status to PROCESSING
        withdrawal.setStatus(Withdrawal.WithdrawalStatus.PROCESSING);
        withdrawal = withdrawalRepository.saveAndFlush(withdrawal);

        try {
            String connectedAccountId = user.getStripeConnectedAccountId();
            Account stripeAccount;

            if (connectedAccountId == null || connectedAccountId.isEmpty()) {
                System.out.println("UserService: No Stripe Connected Account found for user: " + user.getId() + ". Creating one.");
                stripeAccount = stripeService.createCustomConnectedAccount(user.getEmail(), "RO");
                connectedAccountId = stripeAccount.getId();
                user.setStripeConnectedAccountId(connectedAccountId);
                System.out.println("UserService: Created Stripe Connected Account ID: " + connectedAccountId + " for user: " + user.getId() + ". User will be saved by transaction.");

                AccountUpdateParams.Individual.Dob dob = AccountUpdateParams.Individual.Dob.builder()
                        .setDay(1L)
                        .setMonth(1L)
                        .setYear(1990L)
                        .build();
                AccountUpdateParams.Individual.Address address = AccountUpdateParams.Individual.Address.builder()
                        .setLine1("Default Address Line 1")
                        .setCity("Default City")
                        .setPostalCode("000000")
                        .setCountry("RO")
                        .build();

                AccountUpdateParams.BusinessProfile businessProfile =
                        AccountUpdateParams.BusinessProfile.builder()
                                .setUrl("https://parkfast.it")
                                .build();

                stripeAccount = stripeService.updateConnectedAccountIndividualDetails(
                        connectedAccountId,
                        Optional.ofNullable(user.getUsername()).orElse(user.getUsername()),
                        "LastName",
                        user.getEmail(),
                        dob,
                        address,
                        Optional.ofNullable(user.getPhoneNumber()).orElse("+40700000000"),
                        null,
                        businessProfile
                );
                System.out.println("UserService: Updated individual details for Connected Account: " + connectedAccountId);

                stripeAccount = stripeService.acceptTosForConnectedAccount(
                        connectedAccountId,
                        "127.0.0.1",
                        Instant.now().getEpochSecond()
                );
                System.out.println("UserService: Updated ToS acceptance for Connected Account: " + connectedAccountId);

                System.out.println("UserService: Adding IBAN " + user.getBankAccountNumber() + " for " + user.getBankAccountName() + " to Connected Account " + connectedAccountId);
                BankAccount externalBankAccount = stripeService.addExternalBankAccountToConnectedAccount(
                        connectedAccountId,
                        user.getBankAccountName(),
                        user.getBankAccountNumber(),
                        true
                );
                System.out.println("UserService: Added bank account " + externalBankAccount.getId() + " to Connected Account: " + connectedAccountId);

                stripeAccount = stripeService.requestCapabilitiesForConnectedAccount(connectedAccountId, List.of("transfers"));
                System.out.println("UserService: Ensured 'transfers' capability for Connected Account: " + connectedAccountId);
            } else {
                System.out.println("UserService: Found existing Stripe Connected Account ID: " + connectedAccountId + " for user: " + user.getId());
                stripeAccount = Account.retrieve(connectedAccountId);
            }

            if (!stripeAccount.getPayoutsEnabled()) {
                String requirementsDetails = stripeAccount.getRequirements() != null ? stripeAccount.getRequirements().toString() : "N/A";
                System.err.println("UserService WARNING: Stripe Connected Account " + connectedAccountId + " is not enabled for payouts. PayoutsEnabled: " + stripeAccount.getPayoutsEnabled() + ". Requirements: " + requirementsDetails);
                if (stripeAccount.getRequirements() != null && !stripeAccount.getRequirements().getCurrentlyDue().isEmpty()){
                    String missingFields = String.join(", ", stripeAccount.getRequirements().getCurrentlyDue());
                    throw new PaymentProcessingException("Stripe Connected Account requires more information to enable payouts: " + missingFields + ". Please update your account details.");
                } else if (!stripeAccount.getPayoutsEnabled()) { // Fallback if currently_due is empty but payouts still disabled
                    throw new PaymentProcessingException("Stripe Connected Account payouts are not enabled. Please check account status and requirements in Stripe dashboard or contact support. Details: " + requirementsDetails);
                }
            }
            if (stripeAccount.getExternalAccounts() == null || stripeAccount.getExternalAccounts().getData().isEmpty()) {
                System.err.println("UserService ERROR: No external bank account found on Connected Account: " + connectedAccountId + ". Cannot make transfer.");
                throw new PaymentProcessingException("Stripe Connect account setup incomplete: Missing bank account on Stripe Connected Account. Please add a bank account.");
            }

            long amountInSmallestUnit = Math.round(amountToWithdraw * 100);
            Map<String, String> transferMetadata = Map.of(
                    "internal_withdrawal_id", withdrawal.getId(),
                    "platform_user_id", user.getId(),
                    "withdrawal_type", "ALL_PARKING_LOTS",
                    "initiated_by_user_login", "simonaAMH"
            );
            String transferGroup = "WITHDRAWAL-" + withdrawal.getId();

            System.out.println("UserService: Attempting to transfer " + amountInSmallestUnit + " RON to Connected Account: " + connectedAccountId + " for withdrawal: " + withdrawal.getId());
            Transfer stripeTransfer = stripeService.createTransferToDestination(
                    amountInSmallestUnit,
                    "RON",
                    connectedAccountId,
                    transferMetadata,
                    transferGroup
            );

            // SUCCESS: Update withdrawal status to COMPLETED
            return updateWithdrawalToCompleted(withdrawalId, stripeTransfer.getId(), user, amountToWithdraw);

        } catch (StripeException e) {
            String errorMessage = getStripeErrorMessage(e);
            System.err.println("UserService StripeException during Connect withdrawal for ID " + withdrawal.getId() + ": " + errorMessage);
            updateWithdrawalToFailed(withdrawalId, "Stripe Error: " + errorMessage);
            throw new PaymentProcessingException(errorMessage);

        } catch (PaymentProcessingException ppe) {
            System.err.println("UserService PaymentProcessingException during Connect withdrawal for ID " + withdrawal.getId() + ": " + ppe.getMessage());
            updateWithdrawalToFailed(withdrawalId, ppe.getMessage());
            throw ppe;

        } catch (Exception e) {
            System.err.println("UserService General Exception during Connect withdrawal for ID " + withdrawal.getId() + ": " + e.getMessage());
            updateWithdrawalToFailed(withdrawalId, "General Error: " + e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new PaymentProcessingException("Withdrawal processing failed due to an unexpected error: " + e.getMessage());
        }
    }

    /**
     * Update withdrawal to completed status in a separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WithdrawalResponseDTO updateWithdrawalToCompleted(String withdrawalId, String stripePayoutId, User user, Double amountToWithdraw) {
        try {
            // Get fresh withdrawal from database
            Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found: " + withdrawalId));

            withdrawal.setStatus(Withdrawal.WithdrawalStatus.COMPLETED);
            withdrawal.setStripePayoutId(stripePayoutId);
            withdrawal.setProcessedAt(OffsetDateTime.now());

            // Update user earnings
            Double currentPending = Optional.ofNullable(user.getPendingEarnings()).orElse(0.0);
            Double currentPaid = Optional.ofNullable(user.getPaidEarnings()).orElse(0.0);
            user.setPendingEarnings(Math.max(0, currentPending - amountToWithdraw));
            user.setPaidEarnings(currentPaid + amountToWithdraw);

            // Save both entities
            withdrawal = withdrawalRepository.saveAndFlush(withdrawal);
            userRepository.saveAndFlush(user);

            // Send confirmation email
            List<ParkingLot> userParkingLots = parkingLotRepository.findByOwner(user);
            String parkingLotNames = userParkingLots.stream()
                    .map(ParkingLot::getName)
                    .collect(Collectors.joining(", "));

            emailService.sendWithdrawalConfirmationEmail(
                    user.getEmail(),
                    withdrawal.getId(),
                    amountToWithdraw,
                    user.getBankAccountNumber(),
                    "All Parking Lots (" + parkingLotNames + ")"
            );

            return convertToWithdrawalResponseDTO(withdrawal);

        } catch (Exception e) {
            System.err.println("UserService: Error updating withdrawal to completed for ID " + withdrawalId + ": " + e.getMessage());
            throw new PaymentProcessingException("Failed to complete withdrawal update: " + e.getMessage());
        }
    }

    /**
     * Update withdrawal to failed status in a separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateWithdrawalToFailed(String withdrawalId, String failureReason) {
        try {
            // Get fresh withdrawal from database
            Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found: " + withdrawalId));

            withdrawal.setStatus(Withdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(failureReason);

            withdrawalRepository.saveAndFlush(withdrawal);
            System.out.println("UserService: Updated withdrawal " + withdrawalId + " to FAILED status");

        } catch (Exception e) {
            System.err.println("UserService: Error updating withdrawal to failed for ID " + withdrawalId + ": " + e.getMessage());
            // Don't throw here to avoid masking the original exception
        }
    }

    /**
     * Extract error message from StripeException
     */
    private String getStripeErrorMessage(StripeException e) {
        if (e.getStripeError() != null && e.getStripeError().getMessage() != null) {
            return e.getStripeError().getMessage();
        } else if (e.getMessage() != null) {
            return e.getMessage();
        } else {
            return "Stripe Error: An unexpected error occurred.";
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

        List<Withdrawal> recentWithdrawals = withdrawalRepository
                .findByUserIdAndRequestedAtAfterOrderByRequestedAtDesc(
                        userId,
                        OffsetDateTime.now().minusMonths(3)
                )
                .stream()
                .limit(5)
                .toList();

        List<ParkingLot> userParkingLots = parkingLotRepository.findByOwner(user);

        // Create a single withdrawal info object representing all parking lots
        boolean canWithdraw = user.getBankAccountName() != null && !user.getBankAccountName().isBlank() &&
                user.getBankAccountNumber() != null && !user.getBankAccountNumber().isBlank() &&
                user.getPendingEarnings() != null &&
                user.getPendingEarnings() > 0 &&
                !userParkingLots.isEmpty();

        String blockReason = null;
        if (userParkingLots.isEmpty()) {
            blockReason = "You don't own any parking lots.";
            canWithdraw = false;
        } else if (user.getBankAccountName() == null || user.getBankAccountName().isBlank() ||
                user.getBankAccountNumber() == null || user.getBankAccountNumber().isBlank()) {
            blockReason = "Bank account details not configured in your profile. Please update your account settings.";
            canWithdraw = false;
        } else if (user.getPendingEarnings() == null || user.getPendingEarnings() <= 0) {
            blockReason = "No pending earnings available for withdrawal.";
            canWithdraw = false;
        } else if (withdrawalRepository.existsByUserIdAndStatusIn(userId, List.of(Withdrawal.WithdrawalStatus.PENDING, Withdrawal.WithdrawalStatus.PROCESSING))) {
            blockReason = "You already have a pending or processing withdrawal. Please wait for it to complete.";
            canWithdraw = false;
        }

        String allParkingLotNames = userParkingLots.stream()
                .map(ParkingLot::getName)
                .collect(Collectors.joining(", "));

        String allParkingLotAddresses = userParkingLots.stream()
                .map(ParkingLot::getAddress)
                .collect(Collectors.joining("; "));

        ParkingLotWithdrawalInfoDTO withdrawalInfo = ParkingLotWithdrawalInfoDTO.builder()
                .id("ALL") // Special ID to indicate all parking lots
                .name("All Parking Lots (" + allParkingLotNames + ")")
                .address(allParkingLotAddresses)
                .bankAccountName(user.getBankAccountName())
                .bankAccountNumber(user.getBankAccountNumber())
                .pendingEarnings(user.getPendingEarnings())
                .canWithdraw(canWithdraw)
                .withdrawalBlockReason(blockReason)
                .build();

        return WithdrawalSummaryDTO.builder()
                .totalPendingEarnings(user.getPendingEarnings())
                .totalPaidEarnings(user.getPaidEarnings())
                .totalWithdrawals((int) withdrawalRepository.countByUserId(userId))
                .recentWithdrawals(recentWithdrawals.stream()
                        .map(this::convertToWithdrawalResponseDTO)
                        .toList())
                .availableParkingLots(List.of(withdrawalInfo)) // Single item representing all parking lots
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
                .amount(withdrawal.getAmount())
                .bankAccountName(withdrawal.getBankAccountName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .status(withdrawal.getStatus())
                .failureReason(withdrawal.getFailureReason())
                .stripePayoutId(withdrawal.getStripePayoutId())
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