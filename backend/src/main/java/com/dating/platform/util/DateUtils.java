package com.dating.platform.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DateUtils {

    public static int ageOf(LocalDate dateOfBirth) {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /** Start of the next UTC day - when daily quotas reset. */
    public static Instant nextDailyReset() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Start of next Monday UTC - when weekly quotas reset. */
    public static Instant nextWeeklyReset() {
        return LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** ISO week key such as {@code 2026-W12}, used to key weekly auto-match runs. */
    public static String isoWeekKey(LocalDate date) {
        int week = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        return year + "-W" + (week < 10 ? "0" + week : week);
    }
}
