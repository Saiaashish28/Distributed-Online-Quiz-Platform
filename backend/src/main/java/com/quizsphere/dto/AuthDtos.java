package com.quizsphere.dto;

import com.quizsphere.entity.Role;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotNull Role role,
            @NotBlank @Size(max = 255) String identifier,
            @NotBlank @Size(max = 128) String password) {
    }

    public record RegisterRequest(
            @NotNull Role role,
            @NotBlank @Size(max = 150) String name,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @Size(max = 100) String inviteCode,
            @Size(max = 40) String registerNumber,
            @Min(1) @Max(4) Integer academicYear,
            @Size(max = 50) String department,
            @Size(max = 20) String section,
            @Size(max = 50) String program,
            @Min(1) @Max(12) Integer semester) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword) {
    }

    public record StudentProfile(Long id, String registerNumber, String fullName, Integer academicYear,
                                 String department, String section, String program, Integer semester,
                                 List<StudentDtos.CourseRef> courses) {
    }

    public record UserInfo(Long id, String name, String email, Role role, boolean mustChangePassword,
                           StudentProfile student) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserInfo user) {
    }

    public record AuthConfig(boolean studentSelfRegistration, boolean adminRegistration) {
    }
}
