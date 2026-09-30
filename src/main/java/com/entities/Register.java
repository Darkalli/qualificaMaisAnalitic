package com.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_register_person_course",
        columnNames = {"person_id", "course_id"}))
@Getter
@Setter
public class Register {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;
    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course courseOfInterest;
    @Column(nullable = false)
    private LocalDate registerDate;

    public Register(Person person, Course courseOfInterest, LocalDate registerDate) {
        this.person = person;
        this.courseOfInterest = courseOfInterest;
        this.registerDate = registerDate;
    }

    public Register() {
    }
}
