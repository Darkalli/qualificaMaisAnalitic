package com.mappers;

import com.dtos.CourseDtos.UpdateCourseDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Course;
import com.entities.Person;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface CourseMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateCoursefromDto(UpdateCourseDto dto, @MappingTarget Course course);
}
