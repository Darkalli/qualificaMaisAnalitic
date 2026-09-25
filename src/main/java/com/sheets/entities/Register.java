package com.sheets.entities;

import com.sheets.enums.Disabilities;
import com.sheets.enums.Education;
import com.sheets.enums.Gender;
import com.sheets.enums.WorkState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_register_cpf", columnNames = "cpf"))
@Getter
@Setter
public class Register {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String fullName;
    @Column(columnDefinition = "text")
    private String socialName;
    @Column(nullable = false, length = 11)
    private String cpf;
    @Column(nullable = false, columnDefinition = "text")
    private String email;
    @Column(nullable = false, length = 11)
    private String personalPhone;
    @Column(nullable = false)
    private Boolean personalPhoneHasWhatsapp;
    @Column(length = 11)
    private String familyPhone;
    @OneToOne(optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "address_id", nullable = false, unique = true)
    private Address address;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Gender gender;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Education education;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private WorkState workState;
    @ElementCollection
    @CollectionTable(name = "register_disabilities", joinColumns = @JoinColumn(name = "register_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"register_id", "disability"}))
    @Column(name = "disability", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private Set<Disabilities> disabilities = EnumSet.noneOf(Disabilities.class);
    @Column(nullable = false, columnDefinition = "text")
    private String courseOfInterest;
    @Column(nullable = false)
    private LocalDate registerDate;
}
