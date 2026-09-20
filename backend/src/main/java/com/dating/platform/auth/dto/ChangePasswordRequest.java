package com.dating.platform.auth.dto;

import com.dating.platform.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(

        @NotBlank(message = "Enter your current password")
        String currentPassword,

        @NotBlank(message = "Choose a new password")
        @StrongPassword
        String newPassword
) {
}
