package com.dating.platform.match.engine;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Result of scoring one candidate pair.
 *
 * @param total      0..1 overall compatibility
 * @param breakdown  per-dimension contribution, kept for explainability ("You both love hiking")
 * @param highlights human readable reasons shown on the auto-match card
 */
@Schema(name = "CompatibilityScore")
public record CompatibilityScore(double total, Map<String, Double> breakdown, List<String> highlights) {

    public static CompatibilityScore zero() {
        return new CompatibilityScore(0d, Map.of(), List.of());
    }
}
