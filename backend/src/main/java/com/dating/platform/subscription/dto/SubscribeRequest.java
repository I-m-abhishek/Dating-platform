package com.dating.platform.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SubscribeRequest")
public record SubscribeRequest(

        @NotBlank(message = "Choose a plan")
        @Size(max = 40)
        String planCode,

        /**
         * Token from the payment provider. This build accepts any non-blank value and
         * records it verbatim; a real integration verifies it server side before granting.
         */
        @Size(max = 200)
        String paymentToken
) {
}
