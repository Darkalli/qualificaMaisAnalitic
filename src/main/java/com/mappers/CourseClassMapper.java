package com.mappers;


import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.CourseClass;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface CourseClassMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateCourseClassfromDto(UpdateCourseClassDto dto, @MappingTarget CourseClass courseClass);
}
