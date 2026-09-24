package com.entities;

import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class Register {
    @Id
    private Long id;

    private String fullName;
    private String socialName;
    private String cpf;
    private String email;
    private String personalPhone;
    private Boolean personalPhoneHasWhatsapp;
    private String familyPhone;
    @OneToOne
    @JoinColumn(name = "address_id")
    private Address address;
    private Gender gender;
    private Education education;
    private WorkState workState;
    @ElementCollection
    @CollectionTable(name = "register_disabilities", joinColumns = @JoinColumn(name = "register_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"register_id", "disability"}))
    @Column(name = "disability", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Disabilities> disabilities = EnumSet.noneOf(Disabilities.class);
    private String courseOfInterest;
    private LocalDate registerDate;
}
