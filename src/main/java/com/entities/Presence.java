package com.entities;

import com.enums.PresenceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "presence", uniqueConstraints = @UniqueConstraint(name = "uk_presence_person_course_date",
        columnNames = {"person_id", "course_id", "date"}))
@Getter
@Setter
public class Presence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    private PresenceStatus status;

    public Presence(Person person, LocalDate date, Course course, PresenceStatus status) {
        this.person = person;
        this.date = date;
        this.course = course;
        this.status = status;
    }

    public Presence() {
    }
}
