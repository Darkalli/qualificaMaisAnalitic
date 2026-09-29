package com.dtos.personDtos;

import com.entities.Address;
import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.util.Set;

public record UpdatePersonDto(String Cpf, @JsonProperty(required = false)String socialName,
                              @JsonProperty(required = false) String email, @JsonProperty(required = false)String personalPhone,
                              @JsonProperty(required = false) String familyPhone, @JsonProperty(required = false) Address address,
                              @JsonProperty(required = false) Gender gender, @JsonProperty(required = false) Education education,
                              @JsonProperty(required = false) WorkState workState, @JsonProperty(required = false) Set<Disabilities> disabilities) {
}
