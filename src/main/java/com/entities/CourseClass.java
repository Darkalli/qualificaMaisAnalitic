package com.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "course_class", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"course_id", "day"})
})
public class CourseClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_day")
    private LocalDate day;
    private String session;
    private LocalDateTime start;
    private LocalDateTime finish;
    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties("courseClass")
    private Course course;

    public CourseClass(LocalDate day, String session, LocalDateTime start, LocalDateTime finish, Course course) {
        this.day = day;
        this.session = session;
        this.start = start;
        this.finish = finish;
        this.course = course;
    }

    public CourseClass() {
    }
}
