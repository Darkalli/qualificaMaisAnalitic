package com.entities;

import com.enums.PresenceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "presence", uniqueConstraints = @UniqueConstraint(name = "uk_presence_person_class",
        columnNames = {"person_id", "course_class_id"}))
@Getter
@Setter
public class Presence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_class_id", nullable = false)
    private CourseClass courseClass;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    private PresenceStatus status;

    public Presence(Person person, CourseClass courseClass, Course course, PresenceStatus status) {
        this.person = person;
        this.courseClass = courseClass;
        this.course = course;
        this.status = status;
    }

    public Presence() {
    }
}
