package com.careerconnect.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {

    public static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    public static final DateTimeFormatter ISO_LOCAL_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateTimeUtils() {
    }

    public static Instant nowUtc() {
        return Instant.now();
    }

    public static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public static String formatIso(Instant instant) {
        return instant != null ? ISO_INSTANT_FORMATTER.format(instant) : null;
    }

    public static String formatIso(LocalDate date) {
        return date != null ? ISO_LOCAL_DATE_FORMATTER.format(date) : null;
    }

    public static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
