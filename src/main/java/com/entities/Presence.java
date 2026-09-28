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
    @JoinColumn(name = "aluno_id", nullable = false)
    private Person Person;

    private LocalDate data;

    @ManyToOne
    private Course course;

    @Enumerated(EnumType.STRING)
    private PresenceStatus status;


}
