package com.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AttendanceSummary(int present, int absent, int justified, int pending,
                                long attendedMinutes, long totalMinutes) {
    public int totalClasses() {
        return present + absent + justified + pending;
    }

    public BigDecimal percentage() {
        return totalClasses() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(present).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalClasses()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal attendedHours() {
        return BigDecimal.valueOf(attendedMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
