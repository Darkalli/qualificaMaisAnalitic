package com.entities;

import com.enums.StatusRegister;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "register", uniqueConstraints = @UniqueConstraint(name = "uk_register_person_course",
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusRegister status = StatusRegister.ACTIVE;

    public Register(Person person, Course courseOfInterest, LocalDate registerDate, StatusRegister status) {
        this.person = person;
        this.courseOfInterest = courseOfInterest;
        this.registerDate = registerDate;
        this.status = status;
    }

    public Register() {
    }
}
