package com.dtos.registerDtos;

import java.time.LocalDate;

public record AddRegisterDto(String personCpf, Long courseOfInterestId, LocalDate registerDate) {
}
