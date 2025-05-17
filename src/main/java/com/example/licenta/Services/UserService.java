package com.example.licenta.Services;

import com.example.licenta.DTOs.UserPaymentMethodDTO;
import com.example.licenta.DTOs.UserRegistrationDTO;
import com.example.licenta.DTOs.UserUpdateDTO;
import com.example.licenta.DTOs.UserVehiclePlateDTO;
import com.example.licenta.Enum.User.Role;
import com.example.licenta.Exceptions.*;
import com.example.licenta.Models.User;
import com.example.licenta.Models.UserPaymentMethod;
import com.example.licenta.Models.UserVehiclePlate;
import com.example.licenta.Repositories.UserPaymentMethodRepository;
import com.example.licenta.Repositories.UserRepository;
import com.example.licenta.Repositories.UserVehiclePlateRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
                       ImageService imageService) {
        this.userRepository = userRepository;
        this.vehiclePlateRepository = vehiclePlateRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.imageService = imageService;
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

        emailService.sendVerificationEmail(user.getEmail(), token);
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
    public User updateUser(Long userId, UserUpdateDTO updateDto) {
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
            if (!user.getEmail().equals(updateDto.getEmail()) &&
                    userRepository.existsByEmail(updateDto.getEmail())) {
                throw new UserExistsException("Email already in use");
            }

            boolean emailChanged = !updateDto.getEmail().equals(user.getEmail());
            user.setEmail(updateDto.getEmail());

            if (emailChanged) {
                user.setEmailVerified(false);
                String token = generateToken();
                user.setEmailVerificationToken(token);
                user.setTokenExpiry(OffsetDateTime.now().plusDays(1));
                emailService.sendVerificationEmail(updateDto.getEmail(), token);
            }
        }

        if (updateDto.getProfileImage() == null || updateDto.getProfileImage().isEmpty()) {
            user.setProfileImage(null);
        } else {
            try {
                String imageUrl = imageService.saveImage(updateDto.getProfileImage());
                user.setProfileImage(imageUrl);
            } catch (IOException e) {
                throw new InvalidDataException("Failed to save profile image: " + e.getMessage());
            }
        }

        if (updateDto.getCurrentPassword() != null && updateDto.getNewPassword() != null) {
            if (!passwordEncoder.matches(updateDto.getCurrentPassword(), user.getPassword())) {
                throw new InvalidCredentialsException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(updateDto.getNewPassword()));
        }

        if (updateDto.getRole() != null) {
            user.setRole(updateDto.getRole());
        }

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
    public User useLoyaltyPoints(Long userId, int pointsToUse) {
        if (pointsToUse <= 0) {
            throw new InvalidDataException("Points to use must be positive.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getLoyaltyPoints() < pointsToUse) {
            throw new InvalidDataException("Insufficient loyalty points. Available: " + user.getLoyaltyPoints());
        }

        int newPointsBalance = user.getLoyaltyPoints() - pointsToUse;
        user.setLoyaltyPoints(newPointsBalance);
        user.setUpdatedAt(OffsetDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public UserVehiclePlate addVehiclePlate(Long userId, UserVehiclePlateDTO plateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (vehiclePlateRepository.existsByUserIdAndPlateNumber(userId, plateDto.getPlateNumber())) {
            throw new InvalidDataException("This vehicle plate is already registered");
        }

        UserVehiclePlate plate = new UserVehiclePlate();
        plate.setUser(user);
        plate.setPlateNumber(plateDto.getPlateNumber());

        return vehiclePlateRepository.save(plate);
    }

    @Transactional
    public void deleteVehiclePlate(Long userId, Long plateId) {
        UserVehiclePlate plate = vehiclePlateRepository.findByUserIdAndId(userId, plateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle plate not found with ID: " + plateId + " for user: " + userId));

        vehiclePlateRepository.delete(plate);
    }

    @Transactional(readOnly = true)
    public List<UserVehiclePlate> getVehiclePlates(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }

        return vehiclePlateRepository.findByUserId(userId);
    }

    @Transactional
    public UserPaymentMethod addPaymentMethod(Long userId, UserPaymentMethodDTO methodDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        UserPaymentMethod method = new UserPaymentMethod();
        method.setUser(user);
        method.setCardNumber(methodDto.getCardNumber());
        method.setExpiryMonth(methodDto.getExpiryMonth());
        method.setExpiryYear(methodDto.getExpiryYear());

        return paymentMethodRepository.save(method);
    }

    @Transactional
    public void deletePaymentMethod(Long userId, Long methodId) {
        UserPaymentMethod method = paymentMethodRepository.findByUserIdAndId(userId, methodId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment method not found with ID: " + methodId + " for user: " + userId));

        paymentMethodRepository.delete(method);
    }

    @Transactional(readOnly = true)
    public List<UserPaymentMethod> getPaymentMethods(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }

        return paymentMethodRepository.findByUserId(userId);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public String generateToken() {
        return UUID.randomUUID().toString();
    }
}