package com.entities;

import com.enums.PresenceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Presence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    private Person Person;

    private LocalDate data;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    private PresenceStatus status;

    public Presence(Person person, LocalDate data, Course course, PresenceStatus status) {
        Person = person;
        this.data = data;
        this.course = course;
        this.status = status;
    }
}
