package com.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Entity
@Getter
@Setter
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private LocalDate start;
    private LocalDate finish;
    @OneToMany
    private ArrayList<CourseClass> courseClass;

    public Course(String name,@Nullable String description, LocalDate start, LocalDate finish, ArrayList<CourseClass> courseClass) {
        this.name = name;
        this.description = description;
        this.start = start;
        this.finish = finish;
        this.courseClass = courseClass;
    }

    public Course() {
    }
}
