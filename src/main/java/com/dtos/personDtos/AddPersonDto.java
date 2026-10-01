package com.dtos.personDtos;

import com.entities.Address;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import jakarta.annotation.Nullable;

import java.util.Set;

public record AddPersonDto(String fullName, @Nullable String socialName, String cpf,
                           String email, String personalPhone, Boolean personalPhoneHasWhatsapp,
                           @Nullable String familyPhone, Address address, Gender gender,
                           Education education, WorkState workState, Set<String> disabilities) {
}
