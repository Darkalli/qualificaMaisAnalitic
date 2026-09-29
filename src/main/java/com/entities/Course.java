package com.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @OneToMany(mappedBy = "course")
    private List<CourseClass> courseClass = new ArrayList<>();

    public Course(String name,@Nullable String description, LocalDate start, LocalDate finish) {
        this.name = name;
        this.description = description;
        this.start = start;
        this.finish = finish;
    }

    public Course() {
    }
}
