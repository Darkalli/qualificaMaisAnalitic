package com.mappers;

import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Person;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface PersonMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "disabilities", ignore = true) // Convertidas e validadas pelo serviço antes de aplicar o PATCH.
    @Mapping(target = "address", ignore = true) // O serviço preserva o vínculo e aplica apenas os campos enviados.
    void updatePersonfromDto(UpdatePersonDto dto, @MappingTarget Person person);
}
