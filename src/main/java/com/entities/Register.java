package com.entities;

import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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
    @OneToOne
    @JoinColumn(name = "address_id")
    private Address address;
    private Gender gender;
    private Education education;
    private WorkState workState;
    private Disabilities disabilities;
    private String courseOfInterest;
    private LocalDate registerDate;
}
