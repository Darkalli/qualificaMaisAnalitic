package com.utils;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Register;
import com.enums.StatusClass;
import com.repositories.RegisterRepository;

import java.util.List;

public class RegisterUtils {
    public static boolean hasScheduleConflict(RegisterRepository registerRepository, Register newRegister) {
        Course newCourse = newRegister.getCourseOfInterest();

        List<Register> registers =
                registerRepository.findByPerson_Cpf(
                        newRegister.getPerson().getCpf()
                );

        for (Register register : registers) {
            if (newRegister.getId() != null && newRegister.getId().equals(register.getId())) {
                continue;
            }
            Course course = register.getCourseOfInterest();
            for (CourseClass courseClass : course.getCourseClass()) {
                for (CourseClass newCourseClass : newCourse.getCourseClass()) {
                    if (classesConflict(newCourseClass, courseClass)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasScheduleConflict(RegisterRepository repository, String cpf, CourseClass changedClass) {
        for (Register register : repository.findByPerson_Cpf(cpf)) {
            for (CourseClass existing : register.getCourseOfInterest().getCourseClass()) {
                if (changedClass.getId() != null && changedClass.getId().equals(existing.getId())) {
                    continue;
                }
                if (classesConflict(changedClass, existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean classesConflict(CourseClass first, CourseClass second) {
        return first.getStatusClass() == StatusClass.ACTIVE
                && second.getStatusClass() == StatusClass.ACTIVE
                && first.getDay().equals(second.getDay())
                && first.getStart().isBefore(second.getFinish())
                && first.getFinish().isAfter(second.getStart());
    }
}
