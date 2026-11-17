package com.reports;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.reports")
public class ReportProperties {
    private String organization = "Qualifica+";
    private ZoneId zone = ZoneId.of("America/Sao_Paulo");
    private Map<Long, CourseSettings> courses = new HashMap<>();

    public CourseSettings course(Long courseId) {
        return courses.getOrDefault(courseId, new CourseSettings());
    }

    @Getter
    @Setter
    public static class CourseSettings {
        private List<String> signatories = List.of();
        private BigDecimal minimumAttendancePercentage;
        private BigDecimal minimumAttendedHours = BigDecimal.ZERO;
    }
}
