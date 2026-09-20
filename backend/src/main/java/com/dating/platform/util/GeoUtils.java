package com.dating.platform.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Great-circle helpers.
 *
 * <p>We keep coordinates as plain columns and filter with a bounding box before the
 * exact haversine test - that keeps the query index-friendly on vanilla PostgreSQL.
 * Swap in PostGIS if the candidate pool ever outgrows this.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double KM_PER_DEGREE_LAT = 111.32;

    /** Distance between two coordinates in kilometres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double latDelta(double radiusKm) {
        return radiusKm / KM_PER_DEGREE_LAT;
    }

    public static double lonDelta(double radiusKm, double atLatitude) {
        double scale = Math.cos(Math.toRadians(atLatitude));
        if (Math.abs(scale) < 1e-6) {
            return 180.0;
        }
        return radiusKm / (KM_PER_DEGREE_LAT * scale);
    }

    /** Rounds a distance the way the UI shows it, so two users never see different numbers. */
    public static int displayDistanceKm(double exactKm) {
        return exactKm < 1 ? 1 : (int) Math.round(exactKm);
    }

    /**
     * Snaps a coordinate to a coarse grid before storing it for discovery.
     * Protects users from trilateration while keeping distance filters useful.
     */
    public static double fuzz(double coordinate, double gridDegrees) {
        return Math.round(coordinate / gridDegrees) * gridDegrees;
    }
}
