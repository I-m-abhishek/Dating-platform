package com.dating.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateLocationRequest")
public record UpdateLocationRequest(

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double longitude,

        @Size(max = 120) String city,

        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2 letter ISO code")
        String country
) {
}
