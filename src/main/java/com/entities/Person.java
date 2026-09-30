package com.entities;

import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "person", uniqueConstraints = @UniqueConstraint(name = "uk_person_cpf", columnNames = "cpf"))
@Getter
@Setter
public class Person {

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
    @CollectionTable(name = "person_disabilities", joinColumns = @JoinColumn(name = "person_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_person_disability", columnNames = {"person_id", "disability"}))
    @Column(name = "disability", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private Set<Disabilities> disabilities = EnumSet.noneOf(Disabilities.class);

    public Person(String fullName,@Nullable String socialName, String cpf, String email, String personalPhone, Boolean personalPhoneHasWhatsapp, @Nullable String familyPhone, Address address, Gender gender, Education education, WorkState workState, Set<Disabilities> disabilities) {
        this.fullName = fullName;
        this.socialName = socialName;
        this.cpf = cpf;
        this.email = email;
        this.personalPhone = personalPhone;
        this.personalPhoneHasWhatsapp = personalPhoneHasWhatsapp;
        this.familyPhone = familyPhone;
        this.address = address;
        this.gender = gender;
        this.education = education;
        this.workState = workState;
        this.disabilities = disabilities;
    }

    public Person() {
    }
}
