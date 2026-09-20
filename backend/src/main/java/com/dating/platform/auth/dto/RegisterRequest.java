package com.dating.platform.auth.dto;

import com.dating.platform.common.validation.Adult;
import com.dating.platform.common.validation.StrongPassword;
import com.dating.platform.user.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

@Schema(name = "RegisterRequest")
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 180)
        String email,

        @NotBlank(message = "Password is required")
        @StrongPassword
        String password,

        @NotBlank(message = "Tell us your name")
        @Size(min = 2, max = 60, message = "Name must be between 2 and 60 characters")
        @Pattern(regexp = "^[\\p{L}\\p{M} '.-]+$", message = "Name contains unsupported characters")
        String displayName,

        @NotNull(message = "Date of birth is required")
        @Adult
        LocalDate dateOfBirth,

        @NotNull(message = "Tell us your gender")
        Gender gender,

        @NotEmpty(message = "Choose who you want to see")
        Set<Gender> interestedIn
) {
}
