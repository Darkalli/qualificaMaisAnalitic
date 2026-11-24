package com.dtos.registerDtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AddRegisterDto(String personCpf, Long courseOfInterestId) {
}
