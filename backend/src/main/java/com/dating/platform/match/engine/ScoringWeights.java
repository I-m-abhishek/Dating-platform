package com.dating.platform.match.engine;

/**
 * Relative importance of each compatibility dimension. They sum to 1.0.
 *
 * <p>Kept as a separate type so weights can later be A/B tested or personalised
 * without touching the scorer itself.
 */
public record ScoringWeights(
        double interests,
        double qualities,
        double intent,
        double lifestyle,
        double age,
        double distance,
        double effort
) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(0.24, 0.20, 0.16, 0.14, 0.10, 0.10, 0.06);
    }

    public double sum() {
        return interests + qualities + intent + lifestyle + age + distance + effort;
    }
}
