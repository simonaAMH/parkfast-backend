package com.example.licenta.Controllers;

import com.example.licenta.JwtComponents.JwtTokenProvider;
import com.example.licenta.DTOs.*;
import com.example.licenta.Exceptions.*;
import com.example.licenta.Models.User;
import com.example.licenta.Models.UserVehiclePlate;
import com.example.licenta.Services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @Autowired
    public UserController(UserService userService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@Valid @RequestBody UserRegistrationDTO registrationDto) {
        User user = userService.registerUser(registrationDto);
        UserDTO userDto = convertToDto(user);

        ApiResponse<UserDTO> response = new ApiResponse<>(
                true,
                HttpStatus.CREATED.value(),
                "User registered successfully. Please check your email to verify your account.",
                userDto
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody UserLoginDTO loginRequest) {
        Optional<User> userOptional = userService.loginUser(
                loginRequest.getUsernameOrEmail(),
                loginRequest.getPassword()
        );

        if (!userOptional.isPresent()) {
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        User user = userOptional.get();
        String token = tokenProvider.generateTokenFromUser(user, loginRequest.isRememberMe());
        String refreshToken = tokenProvider.generateRefreshToken(user);
        UserDTO userDto = convertToDto(user);

        LoginResponseDTO loginResponseDTO = new LoginResponseDTO(token, userDto, "Bearer", refreshToken);

        ApiResponse<LoginResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Authentication successful",
                loginResponseDTO
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidDataException("Verification token is required");
        }

        userService.verifyEmail(token);

        ApiResponse<Void> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Email verified successfully",
                null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam @Valid String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidDataException("Email is required");
        }

        userService.initiatePasswordReset(email);

        ApiResponse<Void> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Password reset email sent",
                null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> resetPassword(@Valid @RequestBody PasswordResetRequestDTO resetRequest) {
        User user = userService.resetPassword(resetRequest.getToken(), resetRequest.getNewPassword());

        String jwtToken = tokenProvider.generateTokenFromUser(user, false);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        UserDTO userDto = convertToDto(user);

        LoginResponseDTO loginResponseDTO = new LoginResponseDTO(jwtToken, userDto, "Bearer", refreshToken);

        ApiResponse<LoginResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Password reset successful",
                loginResponseDTO
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/request-deletion")
    public ResponseEntity<ApiResponse<Void>> requestDeletion(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidDataException("Email is required");
        }

        userService.initiateAccountDeletion(email);

        ApiResponse<Void> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Account deletion email sent",
                null
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@RequestParam String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new InvalidDataException("Deletion token is required");
        }

        userService.deleteAccount(token);

        ApiResponse<Void> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Account deleted successfully",
                null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidDataException("Email is required");
        }

        try {
            userService.resendVerificationEmail(email);

            ApiResponse<Void> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Verification email sent successfully",
                    null
            );

            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            ApiResponse<Void> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "If your email is registered, you will receive a verification link shortly",
                    null
            );

            return ResponseEntity.ok(response);
        }
    }


    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new InvalidDataException("Valid user ID is required");
        }

        Optional<User> userOptional = userService.findById(userId);

        if (userOptional.isEmpty()) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }

        UserDTO userDto = convertToDto(userOptional.get());

        ApiResponse<UserDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "User retrieved successfully",
                userDto
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable String userId,
                                                           @Valid @RequestBody UserUpdateDTO updateDto) {
        if (userId == null || userId.isEmpty()) {
            throw new InvalidDataException("Valid user ID is required");
        }

        User updatedUser = userService.updateUser(userId, updateDto);
        UserDTO userDto = convertToDto(updatedUser);

        ApiResponse<UserDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "User updated successfully",
                userDto
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenRefreshResponseDTO>> refreshToken(@Valid @RequestBody TokenRefreshRequestDTO request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String userId = tokenProvider.getUserIdFromJWT(refreshToken);

        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newAccessToken = tokenProvider.generateTokenFromUser(user, false);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);
        UserDTO userDto = convertToDto(user);

        TokenRefreshResponseDTO refreshResponseDTO = new TokenRefreshResponseDTO(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                userDto
        );

        ApiResponse<TokenRefreshResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Token refreshed successfully",
                refreshResponseDTO
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/vehicle-plates")
    public ResponseEntity<ApiResponse<UserVehiclePlateDTO>> addVehiclePlate(
            @PathVariable String userId,
            @Valid @RequestBody UserVehiclePlateDTO plateDto) {

        UserVehiclePlate savedPlate = userService.addVehiclePlate(userId, plateDto);
        UserVehiclePlateDTO responseDto = convertToPlateDto(savedPlate);

        ApiResponse<UserVehiclePlateDTO> response = new ApiResponse<>(
                true,
                HttpStatus.CREATED.value(),
                "Vehicle plate added successfully",
                responseDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}/vehicle-plates")
    public ResponseEntity<ApiResponse<List<UserVehiclePlateDTO>>> getVehiclePlates(
            @PathVariable String userId) {

        List<UserVehiclePlate> plates = userService.getVehiclePlates(userId);
        List<UserVehiclePlateDTO> plateDtos = plates.stream()
                .map(this::convertToPlateDto)
                .collect(Collectors.toList());

        ApiResponse<List<UserVehiclePlateDTO>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Vehicle plates retrieved successfully",
                plateDtos
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/vehicle-plates/{plateId}")
    public ResponseEntity<ApiResponse<Void>> deleteVehiclePlate(
            @PathVariable String userId,
            @PathVariable String plateId) {

        userService.deleteVehiclePlate(userId, plateId);

        ApiResponse<Void> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Vehicle plate deleted successfully",
                null
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/withdrawals")
    public ResponseEntity<ApiResponse<WithdrawalResponseDTO>> requestWithdrawal(
            @PathVariable String userId) {

        User currentUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        if (!currentUser.getId().equals(userId)) {
            throw new InvalidDataException("You can only request withdrawals for your own account");
        }

        WithdrawalResponseDTO withdrawal = userService.requestWithdrawal(userId);

        ApiResponse<WithdrawalResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.CREATED.value(),
                "Withdrawal request submitted successfully",
                withdrawal
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}/withdrawals")
    public ResponseEntity<ApiResponse<Page<WithdrawalResponseDTO>>> getUserWithdrawals(
            @PathVariable String userId,
            Pageable pageable) {

        User currentUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        Page<WithdrawalResponseDTO> withdrawals = userService.getUserWithdrawals(userId, pageable);

        ApiResponse<Page<WithdrawalResponseDTO>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Withdrawals retrieved successfully",
                withdrawals
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/withdrawal-summary")
    public ResponseEntity<ApiResponse<WithdrawalSummaryDTO>> getWithdrawalSummary(
            @PathVariable String userId) {

        User currentUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        WithdrawalSummaryDTO summary = userService.getWithdrawalSummary(userId);

        ApiResponse<WithdrawalSummaryDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Withdrawal summary retrieved successfully",
                summary
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/portfolio-analytics")
    public ResponseEntity<ApiResponse<PortfolioAnalyticsDTO>> getPortfolioAnalytics(
            @PathVariable String userId,
            @RequestParam(name = "period", defaultValue = "7d") String period) {

        User currentUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        PortfolioAnalyticsDTO summary = userService.getPortfolioAnalytics(userId, period);

        ApiResponse<PortfolioAnalyticsDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Portfolio Analytics retrieved successfully",
                summary
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/withdrawals/{withdrawalId}")
    public ResponseEntity<ApiResponse<WithdrawalResponseDTO>> getWithdrawalById(
            @PathVariable String userId,
            @PathVariable String withdrawalId) {

        User currentUser = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        WithdrawalResponseDTO withdrawal = userService.getWithdrawalById(userId, withdrawalId);

        ApiResponse<WithdrawalResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Withdrawal details retrieved successfully",
                withdrawal
        );

        return ResponseEntity.ok(response);
    }

    private UserDTO convertToDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setProfileImage(user.getProfileImage());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setRole(user.getRole());
        dto.setLoyaltyPoints(user.getLoyaltyPoints());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setPaidEarnings(user.getPaidEarnings());
        dto.setPendingEarnings(user.getPendingEarnings());
        dto.setTotalEarnings(user.getTotalEarnings());
        dto.setBankAccountName(user.getBankAccountName());
        dto.setBankAccountNumber(user.getBankAccountNumber());
        return dto;
    }

    private UserVehiclePlateDTO convertToPlateDto(UserVehiclePlate plate) {
        UserVehiclePlateDTO dto = new UserVehiclePlateDTO();
        dto.setId(plate.getId());
        dto.setPlateNumber(plate.getPlateNumber());
        return dto;
    }
}