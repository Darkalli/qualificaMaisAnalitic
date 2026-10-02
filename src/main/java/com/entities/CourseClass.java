package com.entities;

import com.enums.StatusClass;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "course_class", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_class_course_day", columnNames = {"course_id", "class_day"})
})
public class CourseClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_day", nullable = false)
    private LocalDate day;
    @Column(nullable = false)
    private String session;
    @Column(nullable = false)
    private LocalTime start;
    @Column(nullable = false)
    private LocalTime finish;
    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    @JsonIgnoreProperties("courseClass")
    private Course course;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_class", nullable = false)
    private StatusClass statusClass = StatusClass.ACTIVE;

    public CourseClass(LocalDate day, String session, LocalTime start, LocalTime finish, Course course) {
        this(day, session, start, finish, course, StatusClass.ACTIVE);
    }

    public CourseClass(LocalDate day, String session, LocalTime start, LocalTime finish, Course course,  StatusClass statusClass) {
        this.day = day;
        this.session = session;
        this.start = start;
        this.finish = finish;
        this.course = course;
        this.statusClass = statusClass;
    }

    public CourseClass() {
    }
}
