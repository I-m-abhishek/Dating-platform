package com.dating.platform.subscription.dto;

import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.entity.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(name = "PlanResponse")
public record PlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        PlanTier tier,
        BigDecimal price,
        String currency,
        int billingPeriodMonths,
        List<String> highlights,
        List<Feature> features
) {
}
